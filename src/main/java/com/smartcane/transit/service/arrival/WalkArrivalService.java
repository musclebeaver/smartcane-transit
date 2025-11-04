package com.smartcane.transit.service.arrival;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.response.*;
import com.smartcane.transit.util.GeoUtils;
import com.smartcane.transit.util.PolylineSnapper;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 보행(WALK) 구간의 진행/도착 판정을 담당.
 * - 기본은 step.linestring으로 판정
 * - step에 라인스트링이 없을 때는 leg.passShape.linestring으로 대체
 * - lookAheadM이 설정되면, '곧 도달' 시 다음 안내문을 프리뷰
 */
@Service
public class WalkArrivalService {

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

        String currentInst = null; // 현재 스텝의 안내 문구
        String line = null;  // 판정에 사용할 라인스트링


        // 1) 우선 step.linestring 사용
        if (leg.steps() != null && req.stepIndex() != null) {
            var step = safeGet(leg.steps(), req.stepIndex());
            if (step != null) {
                currentInst = step.description();
                line = step.linestring();
            }
        }
        // 2) 스텝 라인이 없으면 leg.passShape.linestring 사용(FALLBACK)
        if (line == null || line.isBlank()) {
            if (leg.passShape() != null) line = leg.passShape().linestring();
        }

        // 3) 남은거리/도착판정
        var pts = GeoUtils.parseLineString(line);
        double total = GeoUtils.polylineLength(pts);
        var snap = PolylineSnapper.snapToPolyline(req.currLat(), req.currLon(), pts);
        double remaining = Math.max(0, total - snap.snappedMetersFromStart);
        boolean arrived = remaining <= req.arriveRadiusM();


        // 4) 다음 안내(스텝 인덱스/레그 인덱스)
        String nextInstruction = null;
        Integer nextLegIndex   = null;
        Integer nextStepIndex  = null;

        if (arrived) {
            // 도착: 다음 스텝으로 전진, 끝이면 다음 레그로
            if (leg.steps() != null && req.stepIndex() != null) {
                int ni = req.stepIndex() + 1;
                if (ni < leg.steps().size()) {
                    nextStepIndex = ni;
                    nextInstruction = leg.steps().get(ni).description();
                } else {
                    nextLegIndex = req.legIndex() + 1;
                }
            } else {
                nextLegIndex = req.legIndex() + 1;
            }
        } else if (req.lookAheadM() != null && remaining <= req.lookAheadM()) {
            // lookAhead: 가까워지면 다음 스텝 문구를 미리 노출(프리뷰)
            if (leg.steps() != null && req.stepIndex() != null) {
                int ni = req.stepIndex() + 1;
                if (ni < leg.steps().size()) {
                    nextInstruction = leg.steps().get(ni).description();
                    nextStepIndex = ni;
                }
            }
        }

        if (currentInst == null || currentInst.isBlank()) currentInst = "직진하세요.";

        return new ArrivalCheckResponse(
                arrived,
                remaining,
                currentInst,
                nextInstruction,
                nextLegIndex,
                nextStepIndex
        );
    }
}
