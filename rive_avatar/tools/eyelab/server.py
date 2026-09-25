#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOOI Eye Lab -- a local web page for editing the morph eye shapes together.

    python rive_avatar/tools/eyelab/server.py            # then open http://localhost:8765

The page (index.html next to this file) edits ONE file:
    rive_avatar/tools/avatar/data/eye_lab.json
which the avatar generator reads (parts/morph.py, faces/morph_faces.py). Claude edits the
same file directly; the page polls it and shows the change within a couple of seconds.

API (JSON):
    GET    /api/data              -> {"version", "shapes", "faces", "motion"}
    GET    /api/version           -> {"version"}
    PUT    /api/shapes/<key>      body = shape           -> {"version"}
    DELETE /api/shapes/<key>                             -> {"version"}
    PUT    /api/faces             body = {"map": {...}}  -> {"version"}
    PUT    /api/motion            body = motion          -> {"version"}
    POST   /api/build             run the generator + `rive . --once`, copy the .riv into the app
Only listens on 127.0.0.1.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
RIVE_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(RIVE_DIR, "tools", "avatar", "data", "eye_lab.json")
APP_RIV = os.path.abspath(os.path.join(RIVE_DIR, "..", "composeApp", "src", "androidMain", "res", "raw", "avatar.riv"))
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

    def do_GET(self):
        if self.path == "/api/data":
            with LOCK:
                return self._json(200, load())
        if self.path == "/api/version":
            with LOCK:
                return self._json(200, {"version": load().get("version", 0)})
        return super().do_GET()

    def do_PUT(self):
        try:
            body = self._body()
        except ValueError:
            return self._json(400, {"error": "ข้อมูลไม่ใช่ JSON"})
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
            else:
                return self._json(404, {"error": "ไม่รู้จัก " + self.path})
            return self._json(200, {"version": save(data)})

    def do_DELETE(self):
        m = re.match(r"^/api/shapes/([^/]+)$", self.path)
        if not m:
            return self._json(404, {"error": "ไม่รู้จัก " + self.path})
        with LOCK:
            data = load()
            key = m.group(1)
            data.get("shapes", {}).pop(key, None)
            data["faces"] = {f: s for f, s in data.get("faces", {}).items() if s != key}
            return self._json(200, {"version": save(data)})

    def do_POST(self):
        if self.path != "/api/build":
            return self._json(404, {"error": "ไม่รู้จัก " + self.path})
        t0 = time.time()
        steps = []
        for cmd in ([sys.executable, os.path.join("tools", "avatar", "build.py"), "scene.rml"],
                    ["rive", ".", "--once"]):
            p = subprocess.run(cmd, cwd=RIVE_DIR, capture_output=True, text=True, encoding="utf-8",
                               errors="replace", shell=(os.name == "nt" and cmd[0] == "rive"))
            steps.append({"cmd": " ".join(cmd[-3:]), "ok": p.returncode == 0,
                          "out": (p.stdout + p.stderr).strip().splitlines()[-4:]})
            if p.returncode != 0:
                return self._json(500, {"ok": False, "steps": steps})
        riv = os.path.join(RIVE_DIR, "build", "rive_avatar.riv")
        shutil.copyfile(riv, APP_RIV)
        return self._json(200, {"ok": True, "steps": steps, "bytes": os.path.getsize(riv),
                                "seconds": round(time.time() - t0, 1)})


if __name__ == "__main__":
    if not os.path.exists(DATA):
        sys.exit("missing " + DATA)
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("LOOI Eye Lab: http://localhost:%d  (data: %s)" % (PORT, DATA), flush=True)
    srv.serve_forever()
