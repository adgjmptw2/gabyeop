"""식약처 전국통합식품영양성분정보 세 가지(음식·가공식품·원재료성식품)를 받아 앱 자산 catalog.db(SQLite, 읽기 전용)로 만든다.

출처(공공데이터포털, 식품의약품안전처):
  음식      https://www.data.go.kr/data/15100070/standard.do
  가공식품  https://www.data.go.kr/data/15100066/standard.do
  원재료성  https://www.data.go.kr/data/15100065/standard.do
사용: python -I tools/build_catalog.py [내려받은 JSON 캐시 폴더]  → app/src/main/assets/catalog.db

표 food:
  id, name, brand, category, rep, kcal100, carb100, prot100, fat100(100g·100ml 기준),
  serving(1회 제공량), unit(g|ml), pack(제품 한 개 총량, 없으면 NULL), kind(0 음식, 1 원재료, 2 가공식품),
  skey(검색용: 이름+업체 소문자, 공백 제거)
표 meta: version
"""
import json
import re
import sqlite3
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_foods import ORIGIN_RANK, clean, display_name, fmt, num  # noqa: E402

UA = {"User-Agent": "Mozilla/5.0"}
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/catalog.db"
VERSION = "2026-11-09"
SETS = {"dish": "15100070", "processed": "15100066", "raw": "15100065"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r:
        return json.load(r)


def fetch(pk):
    h = get(f"https://www.data.go.kr/download/columList.json?pk={pk}&ext=CSV")
    cols = h["tableVO"]["colNmList"]
    rows, page = [], 1
    while True:
        q = [("colNmList", c) for c in cols] + [
            ("totalCount", h["totalCount"]), ("svcTableNm", h["tableVO"]["svcTableNm"]),
            ("perPage", 5000), ("page", page)]
        part = get(f"https://www.data.go.kr/download/standard.json?publicDataPk={pk}&" + urllib.parse.urlencode(q))
        rows += part
        if len(part) < 5000:
            return rows
        page += 1


def load(name, cache):
    f = cache / f"{name}.json" if cache else None
    if f and f.exists():
        return json.loads(f.read_text(encoding="utf-8"))
    rows = fetch(SETS[name])
    if f:
        f.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    return rows


def amount(s):
    """'30g' '5g(ml)' '1,000 ml' → (30.0, 'g'). 앞에 오는 숫자+단위만 인정."""
    m = re.match(r"\s*([\d,]+(?:\.\d+)?)\s*(g|ml|mL|ML|G)\b", str(s or ""))
    if not m:
        return None, None
    v = num(m.group(1))
    return (v, m.group(2).lower()) if v and v > 0 else (None, None)


NONE = ("", "해당없음", None)
CORP = re.compile(r"\(주\)|㈜|주식회사|\(유\)|유한회사|농업회사법인|영농조합법인|어업회사법인|재단법인|사단법인|\(농\)|\(영\)")
KNOWN = {"씨제이제일제당": "CJ제일제당", "씨제이": "CJ"}


def brand_of(r):
    for k in ("DIST_NM", "MFR_NM"):
        v = r.get(k)
        if v not in NONE:
            v = CORP.sub(" ", v)
            v = re.sub(r"\s*(제\s*\d+\s*)?(공장|사업장|캠퍼스)\s*$", "", v)
            v = re.sub(r"\s+\S*(공장|지점|CAMPUS|캠퍼스)$", "", v, flags=re.I)
            v = clean(v)
            first = v.split(" ")[0] if v else ""
            return KNOWN.get(first, v)
    return ""


def skey(*parts):
    return re.sub(r"\s+", "", " ".join(parts).lower())


def main():
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    out, seen, ids = [], set(), set()

    def add(id_, name, brand, cat, rep, kcal, carb, prot, fat, serving, unit, pack, kind):
        key = (re.sub(r"\s+", "", name).lower(), round(kcal))
        if id_ in ids or (kind != 0 and key in seen):
            return
        seen.add(key)
        ids.add(id_)
        out.append((id_, name, brand, cat, rep, kcal, carb, prot, fat, serving, unit, pack, kind, skey(name, brand)))

    # 음식: 기존 v1.0~1.1과 같은 id·이름 규칙(즐겨찾기·식단 참조 유지)
    dish = sorted(load("dish", cache), key=lambda r: next(
        (i for i, k in enumerate(ORIGIN_RANK) if r["FOOD_ORIGIN_NM"].startswith(k)), len(ORIGIN_RANK)))
    names = set()
    for r in dish:
        kcal = num(r["ENERC"])
        if kcal is None or kcal < 0:
            continue
        name = display_name(r["FOOD_NM"])
        brand = "" if r["REST_NM"] in NONE else clean(r["REST_NM"])
        if (name, brand) in names:
            continue
        names.add((name, brand))
        s, u = amount(r["FOOD_SIZE"])
        base = "ml" if "ml" in r["NUT_CON_SRTR_QUA"] else "g"
        add(r["FOOD_CD"], name, brand, clean(r["FOOD_LV3_NM"]), clean(r["FOOD_LV4_NM"]), kcal,
            num(r["CHOCDF"]), num(r["PROT"]), num(r["FATCE"]), s or 100.0, u or base, None, 0)

    for r in load("raw", cache):
        kcal = num(r["ENERC"])
        if kcal is None or kcal < 0:
            continue
        name = clean(r["FOOD_NM"].replace("_", " "))
        unit = "ml" if "ml" in r["NUT_CON_SRTR_QUA"] else "g"
        add(r["FOOD_CD"], name, "", clean(r["FOOD_LV3_NM"]), clean(r["FOOD_LV4_NM"]), kcal,
            num(r["CHOCDF"]), num(r["PROT"]), num(r["FATCE"]), 100.0, unit, None, 1)

    # 최근 자료가 먼저 남도록
    for r in sorted(load("processed", cache), key=lambda r: r.get("CRT_YMD") or "", reverse=True):
        kcal = num(r["ENERC"])
        if kcal is None or kcal < 0 or not r["FOOD_NM"]:
            continue
        unit = "ml" if "ml" in r["NUT_CON_SRTR_QUA"] else "g"
        pack, _ = amount(r["FOOD_SIZE"])
        serving, _ = amount(r["SERV_SIZE"])
        if not serving or (pack and serving > pack * 1.05):
            serving = pack if pack and pack <= 1000 else 100.0
        rep = r["FOOD_LV4_NM"] if r["FOOD_LV4_NM"] not in NONE else ""
        add(r["FOOD_CD"], clean(r["FOOD_NM"]), brand_of(r), clean(r["FOOD_LV3_NM"]), clean(rep), kcal,
            num(r["CHOCDF"]), num(r["PROT"]), num(r["FATCE"]), serving, unit, pack, 2)

    if OUT.exists():
        OUT.unlink()
    db = sqlite3.connect(OUT)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE food(
          rid INTEGER PRIMARY KEY, id TEXT NOT NULL UNIQUE, name TEXT NOT NULL, brand TEXT NOT NULL,
          category TEXT NOT NULL, rep TEXT NOT NULL, kcal100 REAL NOT NULL, carb100 REAL, prot100 REAL,
          fat100 REAL, serving REAL NOT NULL, unit TEXT NOT NULL, pack REAL, kind INTEGER NOT NULL
        );
        -- 검색은 좁은 표만 훑는다(rowid = food.rid)
        CREATE TABLE fkey(rid INTEGER PRIMARY KEY, skey TEXT NOT NULL);
    """)
    db.executemany("INSERT INTO food VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", [(i, *o[:13]) for i, o in enumerate(out, 1)])
    db.executemany("INSERT INTO fkey VALUES (?,?)", [(i, o[13]) for i, o in enumerate(out, 1)])
    db.execute("INSERT INTO meta VALUES ('version', ?)", (VERSION,))
    db.commit()
    db.execute("VACUUM")
    db.close()
    kinds = [sum(1 for o in out if o[12] == k) for k in range(3)]
    print(f"음식 {kinds[0]} · 원재료 {kinds[1]} · 가공식품 {kinds[2]} = {len(out)}개 → {OUT} ({OUT.stat().st_size // 1024 // 1024} MB)")


if __name__ == "__main__":
    main()
