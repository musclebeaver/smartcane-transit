package com.smartcane.transit.service.arrival;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import com.smartcane.transit.util.GeoUtils;
import com.smartcane.transit.util.PolylineSnapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransitArrivalService {

    private static <T> T safeGet(List<T> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private static ArrivalCheckResponse notFound() {
        return new ArrivalCheckResponse(
                false, 9999.0, "경로를 찾을 수 없습니다.", null, null, null,
                null, null, null, false
        );
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        // (기존 코드와 동일)
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static Integer findNearestStationIndex(List<SkTransitRootDto.StationDto> stations, double currLat, double currLon) {
        // (기존 코드와 동일)
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
            } catch (NumberFormatException ignore) {}
        }
        return bestIdx;
    }

    public ArrivalCheckResponse evaluate(SkTransitRootDto.ItineraryDto itin, ArrivalCheckRequest req) {
        // 1) ~ 5) 기존 로직 동일 (정류장 인덱스 및 stopsLeft 계산)
        var leg = safeGet(itin.legs(), req.legIndex());
        if (leg == null) return notFound();

        String line = (leg.passShape() != null) ? leg.passShape().linestring() : null;
        var pts = GeoUtils.parseLineString(line);
        if (pts == null || pts.isEmpty()) return notFound();

        double total = GeoUtils.polylineLength(pts);
        var snap = PolylineSnapper.snapToPolyline(req.currLat(), req.currLon(), pts);
        double remaining = Math.max(0, total - snap.snappedMetersFromStart);
        boolean arrived = remaining <= req.arriveRadiusM();

        String curr = (leg.start() != null ? leg.start().name() : "") + " → " + (leg.end() != null ? leg.end().name() : "");
        if (curr.isBlank()) curr = "이동 중입니다.";

        Integer currentStationIndex = null;
        Integer stopsLeft = null;

        SkTransitRootDto.PassStopListDto passStopList = leg.passStopList();
        if (passStopList != null && passStopList.stations() != null && !passStopList.stations().isEmpty()) {
            var stations = passStopList.stations();
            currentStationIndex = findNearestStationIndex(stations, req.currLat(), req.currLon());
            if (currentStationIndex != null) {
                int lastIdx = stations.size() - 1;
                stopsLeft = Math.max(0, lastIdx - currentStationIndex);
            }
        }

        Integer nextLegIndex = arrived ? req.legIndex() + 1 : null;

        // ✅ [수정 완료] 파라미터 순서를 DTO 정의에 맞게 수정했습니다.
        // ArrivalCheckResponse(arrived, remaining, currInst, nextInst, nextLeg, nextStep, currStep, currStation, stopsLeft, offRoute)
        return new ArrivalCheckResponse(
                arrived,
                remaining,
                curr,
                null,                // nextInstruction
                nextLegIndex,
                null,                // nextStepIndex
                null,                // currentStepIndex (대중교통은 null)
                currentStationIndex, // currentStationIndex (순서 8번)
                stopsLeft,           // stopsLeft (순서 9번) - 여기가 null로 들어가고 있었습니다!
                false
        );
    }
}