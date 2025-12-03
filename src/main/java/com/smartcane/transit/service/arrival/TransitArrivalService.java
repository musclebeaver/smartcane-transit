package com.smartcane.transit.service.arrival;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import com.smartcane.transit.util.GeoUtils;
import com.smartcane.transit.util.PolylineSnapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 대중교통(BUS / SUBWAY) 구간의 진행/도착 판정을 담당하는 서비스.
 *
 * - 기본 도착 판정 기준:
 *   · leg.passShape.linestring 에 있는 polyline 을 기준으로
 *   · 현재 위치를 가장 가까운 지점으로 스냅한 뒤
 *   · 전체 polyline 길이 - 시작점부터 스냅지점까지의 길이 를 "남은 거리"로 사용
 *
 * - 추가 기능:
 *   · passStopList.stations[] 를 이용해
 *     "현재 위치에서 가장 가까운 정류장 index" 를 찾고,
 *     마지막 정류장까지 남은 정류장 수(N 정거장 남음)를 계산한다.
 *
 * - 출력:
 *   · ArrivalCheckResponse 에 남은 거리, 도착 여부,
 *     현재 안내 문구, nextLegIndex, currentStationIndex, stopsLeft 를 채워서 반환.
 */
@Service
public class TransitArrivalService {

    /** 안전한 리스트 접근용 유틸 */
    private static <T> T safeGet(List<T> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    /** 에러/데이터 없음 상황에서 사용하는 공통 응답 */
    private static ArrivalCheckResponse notFound() {
        return new ArrivalCheckResponse(
                false,
                Double.NaN,
                "경로를 찾을 수 없습니다.",
                null,
                null,
                null,
                null,
                null,             // stopsLeft
                false             // offRoute (일단 false, 나중에 폴리라인 이탈 계산해서 true/false)
        );
    }

    /**
     * Haversine 공식을 이용해 두 좌표 사이 거리(m)를 계산하는 내부 유틸.
     * (GeoUtils 에 distanceMeters 가 이미 있으면 그걸 써도 됨)
     */
    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // 지구 반지름(m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 현재 좌표에서 가장 가까운 정류장 index 를 찾는다.
     *
     * @param stations SK passStopList.stations[]
     * @param currLat  현재 위도
     * @param currLon  현재 경도
     * @return 가장 가까운 정류장 index (없으면 null)
     */
    private static Integer findNearestStationIndex(
            List<SkTransitRootDto.StationDto> stations,
            double currLat,
            double currLon
    ) {
        double best = Double.MAX_VALUE;
        Integer bestIdx = null;

        for (int i = 0; i < stations.size(); i++) {
            SkTransitRootDto.StationDto st = stations.get(i);
            if (st == null || st.lat() == null || st.lon() == null) continue;

            try {
                double sLat = Double.parseDouble(st.lat());
                double sLon = Double.parseDouble(st.lon());
                double d = distanceMeters(currLat, currLon, sLat, sLon);
                if (d < best) {
                    best = d;
                    bestIdx = i;
                }
            } catch (NumberFormatException ignore) {
                // 좌표 파싱 실패 시 해당 정류장은 스킵
            }
        }
        return bestIdx;
    }

    /**
     * 대중교통(BUS / SUBWAY) 구간의 도착 판정 및 남은 정거장 계산.
     *
     * @param itin SK ItineraryDto (한 개의 경로 후보)
     * @param req  ArrivalCheckRequest (현재 위치/판정 파라미터)
     * @return ArrivalCheckResponse
     */
    public ArrivalCheckResponse evaluate(SkTransitRootDto.ItineraryDto itin, ArrivalCheckRequest req) {
        // 1) 현재 진행 중인 leg 추출
        var leg = safeGet(itin.legs(), req.legIndex());
        if (leg == null) return notFound();

        // 2) 노선 형상 라인스트링 파싱 (필수 기준)
        String line = (leg.passShape() != null) ? leg.passShape().linestring() : null;
        var pts = GeoUtils.parseLineString(line);

        // polyline 이 없거나 파싱 실패 시 보호
        if (pts == null || pts.isEmpty()) {
            return notFound();
        }

        // 3) 전체 polyline 길이와, 현재 위치를 스냅한 지점까지의 거리 계산
        double total = GeoUtils.polylineLength(pts);
        var snap = PolylineSnapper.snapToPolyline(req.currLat(), req.currLon(), pts);

        double remaining = Math.max(0, total - snap.snappedMetersFromStart);
        boolean arrived = remaining <= req.arriveRadiusM();

        // 4) 현재 안내 문구(기본: "출발지 → 도착지")
        String curr = (leg.start() != null ? leg.start().name() : "") +
                " → " +
                (leg.end() != null ? leg.end().name() : "");
        if (curr.isBlank()) curr = "이동 중입니다.";

        // 5) 정류장 목록 기반 "N 정거장 남음" 계산
        Integer currentStationIndex = null;
        Integer stopsLeft = null;

        SkTransitRootDto.PassStopListDto passStopList = leg.passStopList();
        if (passStopList != null
                && passStopList.stations() != null
                && !passStopList.stations().isEmpty()) {

            var stations = passStopList.stations();
            currentStationIndex = findNearestStationIndex(stations, req.currLat(), req.currLon());

            if (currentStationIndex != null) {
                int lastIdx = stations.size() - 1;  // 도착 정류장은 리스트의 마지막이라고 가정
                stopsLeft = Math.max(0, lastIdx - currentStationIndex);
            }
        }

        // 6) 도착 시 다음 leg 로 전이 (마지막 leg 를 넘어가면 이후 로직에서 보정)
        Integer nextLegIndex = arrived ? req.legIndex() + 1 : null;

        // 7) 최종 응답 빌드
        return new ArrivalCheckResponse(
                arrived,
                remaining,
                curr,
                null,             // nextInstruction (버스/지하철은 지금은 사용 안 함)
                nextLegIndex,
                null,             // nextStepIndex (대중교통은 step 없음)
                currentStationIndex,
                stopsLeft,             // stopsLeft
                false             // offRoute (일단 false, 나중에 폴리라인 이탈 계산해서 true/false)
        );
    }
}
