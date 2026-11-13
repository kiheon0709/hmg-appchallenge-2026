#!/usr/bin/env python3
"""어린이 보호구역 좌표를 앱 assets 형식(TSV)으로 만든다.

입력은 둘 중 하나:
  1) 공공데이터포털 「전국어린이보호구역표준데이터」 CSV (data.go.kr/data/15012891)
     열: 시설종류, 대상시설명, ..., 위도, 경도, ... (cp949 또는 utf-8)
  2) OpenStreetMap Overpass 응답(JSON). 표준데이터를 받지 못했을 때의 대체.
     초등학교(amenity=school, 이름에 '초등학교')와 유치원(amenity=kindergarten)의 위치.
     보호구역 지정 여부는 OSM에 없으므로 '시설 주변 반경'으로 근사한다.

출력 형식(core SchoolZoneIndex.parse): '#' 줄은 설명, 나머지는 탭 구분 `위도 경도 이름 [제한속도] [반경m]`.
제한속도·반경을 비우면 앱 기본값(30km/h, 300m: 보호구역은 주 출입문 반경 300m 이내에서 지정)을 쓴다.

사용:
  python3 tools/zones/convert_zones.py --fetch-osm 37.0,126.5,38.0,127.6 --out app/src/main/assets/zones/school_zones.tsv
  python3 tools/zones/convert_zones.py --csv 전국어린이보호구역표준데이터.csv --out app/src/main/assets/zones/school_zones.tsv
"""
import argparse
import csv
import datetime
import json
import math
import sys
import urllib.parse
import urllib.request

OVERPASS = "https://overpass-api.de/api/interpreter"


def fetch_osm(bbox: str) -> dict:
    s, w, n, e = bbox.split(",")
    area = f"({s},{w},{n},{e})"
    q = (
        "[out:json][timeout:90];("
        f'nwr["amenity"="school"]["name"~"초등학교"]{area};'
        f'nwr["amenity"="kindergarten"]["name"~"유치원"]{area};'
        ");out center tags;"
    )
    data = urllib.parse.urlencode({"data": q}).encode()
    req = urllib.request.Request(OVERPASS, data=data, headers={"User-Agent": "SideSeat/1.0"})
    with urllib.request.urlopen(req, timeout=150) as r:
        return json.loads(r.read().decode("utf-8"))


def from_osm(doc: dict):
    for el in doc.get("elements", []):
        tags = el.get("tags", {})
        lat = el.get("lat") or (el.get("center") or {}).get("lat")
        lon = el.get("lon") or (el.get("center") or {}).get("lon")
        name = tags.get("name", "").strip()
        if lat is None or lon is None or not name:
            continue
        # 폐교·병설이 아닌 것만. 이름에 '폐교'가 있으면 뺀다.
        if "폐교" in name or tags.get("disused") == "yes":
            continue
        yield float(lat), float(lon), name, None


def from_csv(path: str):
    raw = open(path, "rb").read()
    for enc in ("utf-8-sig", "cp949"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    else:
        sys.exit("CSV 인코딩을 알 수 없어요")
    for row in csv.DictReader(text.splitlines()):
        try:
            lat = float(row.get("위도", "") or "nan")
            lon = float(row.get("경도", "") or "nan")
        except ValueError:
            continue
        if math.isnan(lat) or math.isnan(lon) or not (33 < lat < 39 and 124 < lon < 132):
            continue
        name = (row.get("대상시설명") or "").strip()
        limit = None
        for key in ("제한속도", "제한속도(km/h)"):
            if row.get(key, "").strip().isdigit():
                limit = int(row[key])
        yield lat, lon, name, limit


def dist_m(a, b):
    dy = (a[0] - b[0]) * 111_195
    dx = (a[1] - b[1]) * 111_195 * math.cos(math.radians(a[0]))
    return math.hypot(dx, dy)


def dedupe(rows, within_m=50):
    """같은 이름이 50m 안에 겹치면(노드와 면이 함께 있는 경우) 하나만 남긴다."""
    out = []
    grid = {}
    for r in rows:
        key = (round(r[0], 2), round(r[1], 2))
        near = [o for dy in (-0.01, 0, 0.01) for dx in (-0.01, 0, 0.01)
                for o in grid.get((round(key[0] + dy, 2), round(key[1] + dx, 2)), [])]
        if any(o[2] == r[2] and dist_m(o, r) < within_m for o in near):
            continue
        out.append(r)
        grid.setdefault(key, []).append(r)
    return out


def main():
    ap = argparse.ArgumentParser()
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--csv", help="공공데이터포털 표준데이터 CSV")
    src.add_argument("--osm-json", help="이미 받아 둔 Overpass JSON")
    src.add_argument("--fetch-osm", metavar="S,W,N,E", help="Overpass에서 바로 받기")
    ap.add_argument("--out", required=True)
    ap.add_argument("--area", help="설명에 적을 범위(S,W,N,E). --fetch-osm이면 그 값")
    args = ap.parse_args()

    today = datetime.date.today().isoformat()
    if args.csv:
        rows = list(from_csv(args.csv))
        header = [
            "# 어린이 보호구역 좌표 (앱 K11용)",
            "# 출처: 공공데이터포털 전국어린이보호구역표준데이터 (https://www.data.go.kr/data/15012891/standard.do), 이용허락: 공공누리/포털 정책",
            f"# 변환일: {today}. 좌표는 대상시설 위치. 반경 300m(주 출입문 기준 지정 범위)로 근사.",
        ]
    else:
        doc = json.load(open(args.osm_json)) if args.osm_json else fetch_osm(args.fetch_osm)
        base = doc.get("osm3s", {}).get("timestamp_osm_base", today)
        rows = list(from_osm(doc))
        header = [
            "# 어린이 보호구역 좌표 (앱 K11용) - 대체 데이터",
            "# 출처: © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright). Overpass API로 조회.",
            f"# 데이터 기준: {base}, 변환일: {today}. 범위(S,W,N,E): {args.fetch_osm or args.area or '미기재'}",
            "# 주의: OSM에는 보호구역 지정 정보가 없어 초등학교·유치원 위치 + 반경 300m로 근사했다.",
            "#       공공데이터포털 표준데이터 CSV를 받으면 --csv로 다시 만든다.",
        ]
    rows = dedupe(rows)
    rows.sort(key=lambda r: (round(r[0], 2), round(r[1], 2), r[2]))
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(header) + "\n")
        f.write("# 위도\t경도\t이름\t제한속도\t반경m\n")
        for lat, lon, name, limit in rows:
            f.write(f"{lat:.5f}\t{lon:.5f}\t{name.replace(chr(9), ' ')}\t{limit or ''}\t\n")
    print(f"{len(rows)}곳 → {args.out}")


if __name__ == "__main__":
    main()
