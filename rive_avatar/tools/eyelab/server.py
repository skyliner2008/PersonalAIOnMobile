#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOOI Eye Lab -- a local design table for the eyes and their stories.

    python rive_avatar/tools/eyelab/server.py            # then open http://localhost:8765

The page (index.html next to this file) and Claude share a WORKSPACE:
    tools/eyelab/data/eye_lab.json        shapes, app face table, motion, rig
    tools/eyelab/data/stories/<name>.json timeline stories
The user sketches on the page, Claude refines by editing / generating these files; the page
polls them and shows Claude's changes within a couple of seconds. Nothing here reaches the
Android app until Claude ships it (ship.py -> tools/avatar/data/, then builds the .riv).

API (JSON):
    GET    /api/data              -> {"version", "shapes", "faces", "motion"}
    GET    /api/version           -> {"version", "mtime"}
    PUT    /api/shapes/<key>      body = shape           -> {"version"}
    DELETE /api/shapes/<key>                             -> {"version"}
    PUT    /api/faces             body = {"map": {...}}  -> {"version"}
    PUT    /api/motion            body = motion          -> {"version"}
    PUT    /api/rig               body = rig (eye size, spacing, layer offset, glow) -> {"version"}
    GET    /api/meta              -> {"props", "moods", "faces", "sounds"}  (library props; mood stories a
                                     story may replace, with their Thai name, emotions and sound cues)
    GET    /api/stories           -> [{"name", "by", "basedOn", "intent", "keys", "durationMs", "mtime"}]
    GET    /api/stories/<name>    -> story (+ "mtime")  PUT = save (-> {"mtime"}), DELETE = remove
    GET    /api/app               -> what differs between the workspace and the app (ship.py status)
Only listens on 127.0.0.1.
"""
import json
import os
import re
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ship  # noqa: E402  (status of workspace vs app)
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
RIVE_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(HERE, "data", "eye_lab.json")
STORIES = os.path.join(HERE, "data", "stories")   # one timeline story per file
PRESETS = os.path.join(RIVE_DIR, "presets.json")
PROPS_META = os.path.join(HERE, "props", "props.json")
PORT = int(os.environ.get("EYELAB_PORT", "8765"))
KEY_RE = re.compile(r"^[a-z0-9_]{2,24}$")
LOCK = threading.Lock()


def load():
    with open(DATA, encoding="utf-8") as f:
        return json.load(f)


def save(data):
    """Atomic write, pretty-printed so git diffs stay readable."""
    data["version"] = int(data.get("version", 0)) + 1
    tmp = DATA + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    os.replace(tmp, DATA)
    return data["version"]


def valid_shape(body):
    def pts(v):
        return isinstance(v, list) and len(v) == 8 and all(
            isinstance(p, list) and len(p) == 3 and all(isinstance(n, (int, float)) for n in p) for p in v)
    return isinstance(body, dict) and pts(body.get("left")) and (body.get("right") is None or pts(body.get("right")))


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=HERE, **kw)

    def log_message(self, fmt, *args):  # keep the console quiet except for API writes
        if self.command != "GET":
            sys.stderr.write("[eyelab] %s %s\n" % (self.command, self.path))

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n).decode("utf-8") or "null")

    def _story_path(self, name):
        return os.path.join(STORIES, name + ".json") if KEY_RE.match(name or "") else None

    def do_GET(self):
        if self.path == "/api/meta":
            presets = json.load(open(PRESETS, encoding="utf-8"))
            props = json.load(open(PROPS_META, encoding="utf-8")) if os.path.exists(PROPS_META) else []
            app = {m["name"]: m for m in ship.app_moods()}     # Thai name, emotions, sound cues
            moods = [dict(app.get(q["name"], {}), name=q["name"], durationMs=q["durationMs"])
                     for q in presets["seqs"] if q["name"].startswith("Mood")]
            return self._json(200, {"props": props, "moods": moods, "faces": presets["faces"],
                                    "sounds": ship.robot_sounds()})
        if self.path == "/api/stories":
            os.makedirs(STORIES, exist_ok=True)
            out = []
            for f in sorted(os.listdir(STORIES)):
                if f.endswith(".json"):
                    fp = os.path.join(STORIES, f)
                    st = json.load(open(fp, encoding="utf-8"))
                    out.append({"name": f[:-5], "by": st.get("by", "user"), "basedOn": st.get("basedOn"),
                                "intent": st.get("intent"), "keys": len(st.get("keys", [])),
                                "durationMs": st.get("durationMs"), "mtime": os.path.getmtime(fp)})
            return self._json(200, out)
        m = re.match(r"^/api/stories/([^/]+)$", self.path)
        if m:
            sp = self._story_path(m.group(1))
            if not sp or not os.path.exists(sp):
                return self._json(404, {"error": "ไม่มี story " + m.group(1)})
            st = json.load(open(sp, encoding="utf-8"))
            st["mtime"] = os.path.getmtime(sp)
            return self._json(200, st)
        if self.path == "/api/app":
            return self._json(200, ship.status())
        if self.path == "/api/data":
            with LOCK:
                return self._json(200, load())
        if self.path == "/api/version":
            # the file time too: Claude may edit the JSON without bumping "version"
            with LOCK:
                return self._json(200, {"version": load().get("version", 0), "mtime": os.path.getmtime(DATA)})
        return super().do_GET()

    def do_PUT(self):
        try:
            body = self._body()
        except ValueError:
            return self._json(400, {"error": "ข้อมูลไม่ใช่ JSON"})
        ms = re.match(r"^/api/stories/([^/]+)$", self.path)
        if ms:
            sp = self._story_path(ms.group(1))
            if not sp:
                return self._json(400, {"error": "ชื่อ story ใช้ a-z, 0-9, _ ยาว 2-24 ตัว"})
            if not isinstance(body, dict) or not isinstance(body.get("keys"), list):
                return self._json(400, {"error": "story ต้องมี keys"})
            os.makedirs(STORIES, exist_ok=True)
            body.pop("mtime", None)
            with LOCK:
                tmp = sp + ".tmp"
                with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                    json.dump(body, f, ensure_ascii=False, indent=1)
                    f.write("\n")
                os.replace(tmp, sp)
            return self._json(200, {"ok": True, "mtime": os.path.getmtime(sp)})
        m = re.match(r"^/api/shapes/([^/]+)$", self.path)
        with LOCK:
            data = load()
            if m:
                key = m.group(1)
                if not KEY_RE.match(key):
                    return self._json(400, {"error": "ชื่อรูปใช้ a-z, 0-9, _ ยาว 2-24 ตัว"})
                if not valid_shape(body):
                    return self._json(400, {"error": "รูปตาต้องมี 8 จุด [x, y, ความโค้ง]"})
                body.pop("version", None)
                data.setdefault("shapes", {})[key] = body
            elif self.path == "/api/faces":
                data["faces"] = {k: v for k, v in (body or {}).get("map", {}).items() if v in data.get("shapes", {})}
            elif self.path == "/api/motion":
                data["motion"] = body
            elif self.path == "/api/rig":
                data["rig"] = body
            else:
                return self._json(404, {"error": "ไม่รู้จัก " + self.path})
            return self._json(200, {"version": save(data)})

    def do_DELETE(self):
        ms = re.match(r"^/api/stories/([^/]+)$", self.path)
        if ms:
            sp = self._story_path(ms.group(1))
            if sp and os.path.exists(sp):
                os.remove(sp)
            return self._json(200, {"ok": True})
        m = re.match(r"^/api/shapes/([^/]+)$", self.path)
        if not m:
            return self._json(404, {"error": "ไม่รู้จัก " + self.path})
        with LOCK:
            data = load()
            key = m.group(1)
            data.get("shapes", {}).pop(key, None)
            data["faces"] = {f: s for f, s in data.get("faces", {}).items() if s != key}
            return self._json(200, {"version": save(data)})


if __name__ == "__main__":
    if not os.path.exists(DATA):
        sys.exit("missing " + DATA)
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("LOOI Eye Lab: http://localhost:%d  (data: %s)" % (PORT, DATA), flush=True)
    srv.serve_forever()
