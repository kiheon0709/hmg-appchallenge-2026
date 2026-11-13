#!/usr/bin/env python3
"""시연용 위치 경로(K11 어린이 보호구역 · K12 과속)와 오프라인 제한속도 파일을 만든다.

1) 도로 제한속도(assets/zones/road_limits.json)
   Overpass `way[highway][maxspeed](bbox);out tags geom;` 결과를 줄여 앱에 넣는다. 게이트웨이는 이 범위 안에서는
   외부에 묻지 않는다(시연 때 Overpass가 붐벼도 판정이 된다). 출처: © OpenStreetMap contributors, ODbL.
2) 시연 경로(tools/zones/demo_route_k11_k12.csv)
   OSRM 공개 서버로 실제 도로 경로를 받아 1초 간격 좌표로 나눈다. 구간별 속도:
   - 어린이 보호구역(왕남초등학교 반경 330m 안, 앱 반경 300m): 36km/h(앞 120m부터 감속) → K11 "30 아래로 가요",
     리뷰 "최고 36km/h". 구역 제한 30 + 10을 넘지 않아 K12는 여기서 나오지 않는다.
   - 분당내곡로(OSM maxspeed 80): 합류 뒤 300~1200m 구간 100km/h(+20) → K12 "제한속도가 80이에요"
   - 대왕판교로 등 그 밖: 50km/h, 골목 30km/h. 속도 변화는 초당 8km/h 이내.
3) 재생: tools/zones/replay_geo.sh <csv> (adb emu geo fix를 1초마다).

사용:
  python3 tools/zones/make_demo_route.py --fetch     # Overpass·OSRM에서 받아 둘 다 만든다
  python3 tools/zones/make_demo_route.py --maxspeed ms.json --route route.json
"""
import argparse
import json
import math
import urllib.parse
import urllib.request

import os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BBOX = (37.390, 127.070, 37.440, 127.125)  # S, W, N, E: 에뮬레이터 기본 위치(37.4127, 127.0947) 주변
START = (37.4127, 127.0947)                # 에뮬레이터 기본 위치(성남 수정구 시흥동)
SCHOOL = (37.42845, 127.09837)             # 왕남초등학교(OSM)
VIA_SCHOOL = (37.4278, 127.0990)           # 왕남로, 학교 앞
END = (37.43778, 127.07698)                # 분당내곡로 북쪽 방향

UA = {"User-Agent": "SideSeat/1.0"}


def fetch_maxspeed():
    s, w, n, e = BBOX
    q = f"[out:json][timeout:60];way[highway][maxspeed]({s},{w},{n},{e});out tags geom;"
    req = urllib.request.Request("https://overpass-api.de/api/interpreter",
                                 data=urllib.parse.urlencode({"data": q}).encode(), headers=UA)
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode())


def fetch_route():
    pts = ";".join(f"{lon},{lat}" for lat, lon in (START, VIA_SCHOOL, END))
    url = f"https://router.project-osrm.org/route/v1/driving/{pts}?overview=full&geometries=geojson&continue_straight=true"
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.loads(r.read().decode())


def dist_m(a, b):
    dy = (a[0] - b[0]) * 111_195
    dx = (a[1] - b[1]) * 111_195 * math.cos(math.radians(a[0]))
    return math.hypot(dx, dy)


def bearing(a, b):
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def seg_dist(p, a, b):
    kx = 111_320 * math.cos(math.radians(p[0]))
    ky = 110_540
    px, py = (p[1] - a[1]) * kx, (p[0] - a[0]) * ky
    dx, dy = (b[1] - a[1]) * kx, (b[0] - a[0]) * ky
    l2 = dx * dx + dy * dy
    t = 0 if l2 == 0 else max(0, min(1, (px * dx + py * dy) / l2))
    return math.hypot(px - t * dx, py - t * dy)


def write_road_limits(doc, out):
    ways = []
    for e in doc.get("elements", []):
        if e.get("type") != "way" or "geometry" not in e:
            continue
        tags = {k: v for k, v in e["tags"].items() if k in ("maxspeed", "oneway", "highway", "name")}
        geom = [{"lat": round(p["lat"], 6), "lon": round(p["lon"], 6)} for p in e["geometry"]]
        ways.append({"type": "way", "id": e["id"], "tags": tags, "geometry": geom})
    s, w, n, e = BBOX
    base = doc.get("osm3s", {}).get("timestamp_osm_base", "")
    data = {
        "source": "© OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright)",
        "osm_base": base,
        "bbox": [s, w, n, e],
        "elements": ways,
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"road limits: {len(ways)} ways → {out}")
    return ways


def road_at(p, heading, ways):
    best = None
    for wy in ways:
        g = [(q["lat"], q["lon"]) for q in wy["geometry"]]
        for a, b in zip(g, g[1:]):
            d = seg_dist(p, a, b)
            if d > 25:
                continue
            diff = abs(bearing(a, b) - heading) % 360
            diff = 360 - diff if diff > 180 else diff
            if not (diff <= 30 or (wy["tags"].get("oneway") != "yes" and diff >= 150)):
                continue
            if best is None or d < best[0]:
                best = (d, wy["tags"].get("name", ""), int(wy["tags"]["maxspeed"].split()[0]))
    return best


def make_route(route_doc, ways, out):
    coords = [(lat, lon) for lon, lat in route_doc["routes"][0]["geometry"]["coordinates"]]
    # 경로를 1m 간격 점으로 펴서 걷는다
    dense = [coords[0]]
    for a, b in zip(coords, coords[1:]):
        n = max(1, int(dist_m(a, b)))
        for i in range(1, n + 1):
            dense.append((a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n))
    rows = []
    idx, v, t = 0, 0.0, 0
    merged_at = None
    on_road = 0
    visited_zone = False
    walked = 0.0
    while idx < len(dense) - 1:
        p = dense[idx]
        ahead = dense[min(idx + 15, len(dense) - 1)]
        heading = bearing(p, ahead) if ahead != p else 0
        road = road_at(p, heading, ways)
        # 학교를 지난 뒤 분당내곡로에 10초 넘게 있으면 합류로 본다(나들목에서 스치는 것 제외).
        on_road = on_road + 1 if road and road[1] == "분당내곡로" else 0
        if visited_zone and on_road >= 10 and merged_at is None:
            merged_at = walked
        d_school = dist_m(p, SCHOOL)
        if d_school <= 330:
            visited_zone = True
            target, note = 36, "school_zone"
        elif d_school <= 450 and not visited_zone:
            target, note = 36, "slow_before_zone"
        elif d_school <= 380 and visited_zone and merged_at is None:
            # 앱은 반경 300m + 50m를 벗어나야 나간 것으로 본다. 그때까지 속도 유지.
            target, note = 36, "leaving_zone"
        elif merged_at is not None:
            since = walked - merged_at
            target, note = (100, "speeding_80_zone") if 300 <= since <= 1_200 else (80, f"road_{road[2] if road else '?'}")
        elif idx < 500:
            target, note = 30, "alley"
        else:
            target, note = 50, "arterial"
        v = min(target, v + 8) if v < target else max(target, v - 8)
        rows.append((t, p[0], p[1], v, note, road[2] if road else ""))
        step = max(1, int(round(v / 3.6)))
        idx += step
        walked += step
        t += 1
    end = dense[-1]
    for k in range(3):
        rows.append((t + k, end[0], end[1], 0, "stop", ""))
    with open(out, "w", encoding="utf-8") as f:
        f.write("# 시연 경로: 에뮬레이터 기본 위치 → 왕남초등학교 어린이 보호구역(36km/h) → 분당내곡로(80, 일부 100km/h)\n")
        f.write("# 경로: OSRM(project-osrm.org) 공개 서버, 도로·학교·제한속도 © OpenStreetMap contributors (ODbL)\n")
        f.write("# 1초에 한 줄씩 adb emu geo fix <경도> <위도>. tools/zones/replay_geo.sh로 재생\n")
        f.write("t_sec,lat,lon,kmh,note,map_limit\n")
        for r in rows:
            f.write(f"{r[0]},{r[1]:.6f},{r[2]:.6f},{r[3]:.0f},{r[4]},{r[5]}\n")
    zone = [r for r in rows if r[4] == "school_zone"]
    fast = [r for r in rows if r[4] == "speeding_80_zone"]
    print(f"route: {len(rows)}s → {out}")
    print(f"  school zone t={zone[0][0]}..{zone[-1][0]}s" if zone else "  school zone: none")
    print(f"  speeding t={fast[0][0]}..{fast[-1][0]}s" if fast else "  speeding: none")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--maxspeed")
    ap.add_argument("--route")
    args = ap.parse_args()
    ms = fetch_maxspeed() if args.fetch else json.load(open(args.maxspeed))
    rt = fetch_route() if args.fetch else json.load(open(args.route))
    ways = write_road_limits(ms, f"{ROOT}/app/src/main/assets/zones/road_limits.json")
    make_route(rt, ways, f"{ROOT}/tools/zones/demo_route_k11_k12.csv")


if __name__ == "__main__":
    main()
