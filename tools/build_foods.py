"""식약처 '전국통합식품영양성분정보(음식)표준데이터'를 받아 앱 자산(foods.tsv.gz)으로 만든다.

출처: https://www.data.go.kr/data/15100070/standard.do (식품의약품안전처, 공공데이터포털)
사용: python -I tools/build_foods.py  → app/src/main/assets/foods.tsv.gzbin (AGP가 .gz 자산을 풀어버려서 확장자를 바꿈)
열: id, 이름, 업체(없으면 빈칸), 대분류, 대표식품, kcal/100, 탄수/100, 단백/100, 지방/100, 1회 제공량, 단위(g|ml)
"""
import gzip
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

PK = "15100070"
UA = {"User-Agent": "Mozilla/5.0"}
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/foods.tsv.gzbin"


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return json.load(r)


def fetch_rows():
    h = get(f"https://www.data.go.kr/download/columList.json?pk={PK}&ext=CSV")
    cols = h["tableVO"]["colNmList"]
    rows, page = [], 1
    while True:
        q = [("colNmList", c) for c in cols] + [
            ("totalCount", h["totalCount"]), ("svcTableNm", h["tableVO"]["svcTableNm"]),
            ("perPage", 10000), ("page", page)]
        part = get(f"https://www.data.go.kr/download/standard.json?publicDataPk={PK}&" + urllib.parse.urlencode(q))
        rows += part
        if len(part) < 10000:
            break
        page += 1
    if len(rows) != int(h["totalCount"]):
        sys.exit(f"행 수 불일치: {len(rows)} != {h['totalCount']}")
    return rows


def num(s):
    try:
        return float(str(s).replace(",", ""))
    except ValueError:
        return None


def fmt(v):
    return "" if v is None else (f"{v:.1f}".rstrip("0").rstrip("."))


def display_name(raw):
    """'홍차_홍차 아이스' → '홍차 아이스', '피자_불고기피자 (L)' → '불고기피자 (L)', '커피_큐브 아이스' → '커피 큐브 아이스'"""
    if "_" not in raw:
        return clean(raw)
    head, rest = (clean(x) for x in raw.split("_", 1))
    return rest if head and head in rest else clean(f"{head} {rest}")


def clean(s):
    return re.sub(r"\s+", " ", str(s).replace("\t", " ")).strip()


# 같은 이름·업체가 여러 출처에 있으면 이 순서로 하나만 남김
ORIGIN_RANK = ["가정식", "외식(분석", "외식(프랜차이즈", "외식(재료량", "산업체급식", "중고등학교급식", "초등학교급식"]


def rank(r):
    o = r["FOOD_ORIGIN_NM"]
    return next((i for i, k in enumerate(ORIGIN_RANK) if o.startswith(k)), len(ORIGIN_RANK))


def main():
    rows = sorted(fetch_rows(), key=rank)
    out, skipped, seen = [], 0, set()
    for r in rows:
        kcal = num(r["ENERC"])
        if kcal is None or kcal < 0:
            skipped += 1
            continue
        m = re.match(r"([\d.]+)\s*(g|ml)", str(r["FOOD_SIZE"]).strip(), re.I)
        serving, unit = (num(m.group(1)), m.group(2).lower()) if m else (100.0, "g")
        base_unit = "ml" if "ml" in r["NUT_CON_SRTR_QUA"] else "g"
        brand = "" if r["REST_NM"] in ("해당없음", "", None) else clean(r["REST_NM"])
        name = display_name(r["FOOD_NM"])
        if (name, brand) in seen:
            skipped += 1
            continue
        seen.add((name, brand))
        out.append("\t".join([
            r["FOOD_CD"], name, brand, clean(r["FOOD_LV3_NM"]), clean(r["FOOD_LV4_NM"]),
            fmt(kcal), fmt(num(r["CHOCDF"])), fmt(num(r["PROT"])), fmt(num(r["FATCE"])),
            fmt(serving), unit if m else base_unit,
        ]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wt", encoding="utf-8", compresslevel=9) as f:
        f.write("\n".join(out) + "\n")
    print(f"{len(out)}개 저장, {skipped}개 제외 → {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
