package com.smartcane.transit.service.arrival;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.response.*;
import com.smartcane.transit.util.GeoUtils;
import com.smartcane.transit.util.PolylineSnapper;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 대중교통(BUS/SUBWAY) 구간의 진행/도착 판정을 담당.
 * - 기본은 leg.passShape.linestring(노선 형상)으로 판정
 * - TODO: 추후 passStopList.stations[] 인덱싱 기반 정밀 하차역 판정으로 고도화
 */
@Service
public class TransitArrivalService {

    private static <T> T safeGet(List<T> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }
    private static ArrivalCheckResponse notFound() {
        return new ArrivalCheckResponse(false, Double.NaN, "경로를 찾을 수 없습니다.", null, 0, null);
    }


    public ArrivalCheckResponse evaluate(Itinerary itin, ArrivalCheckRequest req) {
        var leg = safeGet(itin.legs(), req.legIndex());
        if (leg == null) return notFound();

        // 1) 노선 형상 라인(필수)
        String line = leg.passShape() != null ? leg.passShape().linestring() : null;
        var pts = GeoUtils.parseLineString(line);


        // 2) 남은거리/도착판정
        double total = GeoUtils.polylineLength(pts);
        var snap = PolylineSnapper.snapToPolyline(req.currLat(), req.currLon(), pts);
        double remaining = Math.max(0, total - snap.snappedMetersFromStart);
        boolean arrived = remaining <= req.arriveRadiusM();

        // 3) 현재 안내(예: "출발지 → 도착지")

        String curr = (leg.start() != null ? leg.start().name() : "") +
                " → " +
                (leg.end() != null ? leg.end().name() : "");
        if (curr.isBlank()) curr = "이동 중입니다.";


        // 4) 도착 시 다음 레그로 전이(없으면 null)
        Integer nextLegIndex = arrived ? req.legIndex() + 1 : null;



        return new ArrivalCheckResponse(
                arrived,
                remaining,
                curr,
                null,
                nextLegIndex,
                null
        );
    }
}
