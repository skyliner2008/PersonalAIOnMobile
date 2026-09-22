"""ตรวจว่าทุก query ที่ SQLDelight สร้างขึ้น prepare ได้จริงบน SQLite

SQLDelight ขยาย `SELECT *` เป็น `Table.col` เอง — ถ้าห่อด้วย subquery จะได้ SQL ที่ compile ผ่าน
แต่ SQLite ปฏิเสธตอนรัน (เช่น "no such column: ChatMessage.id" ที่ทำให้แอปพังตอนเปิด 2026-09-23)
สคริปต์นี้อ่านโค้ดที่ generate แล้ว สร้าง schema ใน memory แล้ว EXPLAIN ทุก query

ใช้: ./gradlew :composeApp:compileDebugKotlinAndroid แล้ว python tools/verify_sqldelight_queries.py
"""
import glob
import re
import sqlite3
import sys

GEN = "composeApp/build/generated/sqldelight/code/JarvisDatabase/**/*.kt"


def sql_strings(src: str) -> list[str]:
    return [re.sub(r"^\s*\|", "", s, flags=re.M) for s in re.findall(r'"""(.*?)"""', src, re.S)]


def main() -> int:
    files = glob.glob(GEN, recursive=True)
    impl = next((p for p in files if p.endswith("JarvisDatabaseImpl.kt")), None)
    queries = next((p for p in files if p.endswith("JarvisDatabaseQueries.kt")), None)
    if not impl or not queries:
        print("ไม่พบโค้ดที่ generate — รัน :composeApp:compileDebugKotlinAndroid ก่อน", file=sys.stderr)
        return 2

    conn = sqlite3.connect(":memory:")
    create = re.search(r"fun create\(driver.*?\n  \}\n", open(impl, encoding="utf-8").read(), re.S)
    for stmt in sql_strings(create.group(0)):
        conn.execute(stmt)

    failed = 0
    stmts = sql_strings(open(queries, encoding="utf-8").read())
    for stmt in stmts:
        stmt = re.sub(r"\$\w+", "(?)", stmt)  # IN $indexes → IN (?)
        try:
            conn.execute("EXPLAIN " + stmt, [None] * stmt.count("?"))
        except sqlite3.Error as e:
            failed += 1
            print(f"✗ {e} :: {' '.join(stmt.split())[:200]}")
    print(f"query {len(stmts)} รายการ, ผิด {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
