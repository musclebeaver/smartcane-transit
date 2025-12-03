package com.smartcane.transit.service.arrival;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import com.smartcane.transit.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WALK 구간 도착/진행 판정 (step 스냅 + 다음 안내 거리 계산)
 *
 * 역할:
 * 1) 현재 위치에서 가장 가까운 step 인덱스(nearestStepIdx)를 찾는다.
 * 2) 그 step의 description을 currentInstruction 으로 내려준다.
 * 3) 다음 안내 지점까지 거리:
 *    - 아직 중간 step이면: 다음 step의 첫 좌표까지 거리
 *    - 마지막 step이면: leg.end 좌표까지 거리
 *    → "NEXT_STEP:123.45" 형식으로 nextInstruction 에 담는다.
 * 4) remainingMeters 는 leg.end 까지 거리(목적지까지 거리)
 * 5) nextStepIndex 는 "현재 스냅된 step 인덱스"로 항상 세팅
 *    → ProgressCoordinator 가 TripState.stepIndex 를 매번 업데이트 가능
 */
@Service
@Slf4j
public class WalkArrivalService {

    // 폴리라인에서 이 거리보다 멀어지면 경로 이탈로 간주 (m)
    private static final double OFF_ROUTE_THRESHOLD_M = 20.0;

    private static <T> T safeGet(List<T> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private static ArrivalCheckResponse notFound() {
        return new ArrivalCheckResponse(
                false,
                9999.0,
                "경로를 찾을 수 없습니다.",
                null,
                null,
                null,
                null,
                null,
                false
        );
    }

    public ArrivalCheckResponse evaluate(SkTransitRootDto.ItineraryDto itin, ArrivalCheckRequest req) {

        // 1) 현재 leg
        var leg = safeGet(itin.legs(), req.legIndex());
        if (leg == null) {
            log.warn("[WalkArrival] legIndex={} 를 찾지 못했습니다.", req.legIndex());
            return notFound();
        }

        double currLat = req.currLat();
        double currLon = req.currLon();

        // 2) WALK step 리스트
        List<SkTransitRootDto.WalkStepDto> steps = leg.steps();
        if (steps == null || steps.isEmpty()) {
            log.warn("[WalkArrival] WALK leg 이지만 steps 가 비어 있습니다. legIndex={}", req.legIndex());
            // steps 가 없으면 polyline 기준 스냅을 못 하니, leg.end 기준 거리만 내려줌
            return fallbackByLegEndOnly(leg, currLat, currLon, req);
        }

        int nearestStepIdx = -1;
        double bestStepDist = Double.POSITIVE_INFINITY;

        // 3) 현재 위치에서 가장 가까운 step 인덱스 찾기
        for (int i = 0; i < steps.size(); i++) {
            SkTransitRootDto.WalkStepDto step = steps.get(i);
            var pts = GeoUtils.parseLineString(step.linestring()); // [lat, lon]

            for (var pt : pts) {
                double d = GeoUtils.haversine(currLat, currLon, pt[0], pt[1]);
                if (d < bestStepDist) {
                    bestStepDist = d;
                    nearestStepIdx = i;
                }
            }
        }

        boolean offRoute = false;
        if (!Double.isInfinite(bestStepDist) && bestStepDist > OFF_ROUTE_THRESHOLD_M) {
            offRoute = true;
        }

        // 4) leg.end 기준 남은 거리 (목적지까지)
        Double targetLat = null;
        Double targetLon = null;
        if (leg.end() != null) {
            targetLat = leg.end().lat();
            targetLon = leg.end().lon();
        }
        if (targetLat == null || targetLon == null) {
            log.warn("[WalkArrival] legIndex={} end 좌표 없음", req.legIndex());
            return notFound();
        }

        double remaining = GeoUtils.haversine(currLat, currLon, targetLat, targetLon);
        if (Double.isNaN(remaining) || Double.isInfinite(remaining)) {
            log.warn("[WalkArrival] 남은 거리 계산 비정상 remaining={}", remaining);
            return notFound();
        }
        remaining = Math.max(0.0, remaining);
        boolean arrived = remaining <= req.arriveRadiusM();

        // 5) 현재 step 설명
        String currentInst = null;
        if (nearestStepIdx >= 0 && nearestStepIdx < steps.size()) {
            SkTransitRootDto.WalkStepDto step = steps.get(nearestStepIdx);
            if (step.description() != null && !step.description().isBlank()) {
                currentInst = step.description();
            }
        }

        // 6) 다음 안내 지점까지 거리 계산
        //    - 현재 step 이후의 첫 좌표(다음 step 시작점)
        //    - 마지막 step이면 leg.end 까지 거리
        Double nextAnnounceDistM = null;
        if (nearestStepIdx >= 0) {
            int nextIdx = nearestStepIdx + 1;

            if (nextIdx < steps.size()) {
                // 다음 step 의 첫 포인트까지 거리
                SkTransitRootDto.WalkStepDto nextStep = steps.get(nextIdx);
                var ptsNext = GeoUtils.parseLineString(nextStep.linestring());
                if (!ptsNext.isEmpty()) {
                    double[] first = ptsNext.get(0); // [lat, lon]
                    nextAnnounceDistM = GeoUtils.haversine(currLat, currLon, first[0], first[1]);
                }
            } else {
                // 마지막 step 이면 → leg.end 까지 거리 = remaining
                nextAnnounceDistM = remaining;
            }
        }

        String nextInstruction = null;
        if (nextAnnounceDistM != null && !Double.isNaN(nextAnnounceDistM) && !Double.isInfinite(nextAnnounceDistM)) {
            nextAnnounceDistM = Math.max(0.0, nextAnnounceDistM);
            nextInstruction = "NEXT_STEP:" + nextAnnounceDistM;
        }

        // 7) 다음 leg 인덱스 (목적지 도착 시에만)
        Integer nextLegIndex = arrived ? (req.legIndex() + 1) : null;

        // 8) nextStepIndex 는 "현재 스냅된 step 인덱스"로 항상 세팅
        Integer nextStepIndex = (nearestStepIdx >= 0) ? nearestStepIdx : null;

        if (log.isDebugEnabled()) {
            log.debug(
                    "[WalkArrival] leg={} nearestStepIdx={} bestStepDist={}m remainingToEnd={}m nextAnnounceDistM={} arrived={} offRoute={}",
                    req.legIndex(), nearestStepIdx, bestStepDist, remaining, nextAnnounceDistM, arrived, offRoute
            );
        }

        return new ArrivalCheckResponse(
                arrived,
                remaining,        // 목적지(leg 끝)까지 거리
                currentInst,      // 현재 step 설명
                nextInstruction,  // "NEXT_STEP:123.45"
                nextLegIndex,
                nextStepIndex,    // ← 이게 TripState.stepIndex 로 들어감
                null,
                null,
                offRoute
        );
    }

    /**
     * steps 가 비어 있을 때 fallback: leg.end 까지 거리만 보고 안내.
     */
    private ArrivalCheckResponse fallbackByLegEndOnly(
            SkTransitRootDto.LegDto leg,
            double currLat,
            double currLon,
            ArrivalCheckRequest req
    ) {
        Double targetLat = null;
        Double targetLon = null;
        if (leg.end() != null) {
            targetLat = leg.end().lat();
            targetLon = leg.end().lon();
        }
        if (targetLat == null || targetLon == null) {
            return notFound();
        }

        double remaining = GeoUtils.haversine(currLat, currLon, targetLat, targetLon);
        if (Double.isNaN(remaining) || Double.isInfinite(remaining)) {
            return notFound();
        }
        remaining = Math.max(0.0, remaining);
        boolean arrived = remaining <= req.arriveRadiusM();

        Integer nextLegIndex = arrived ? (req.legIndex() + 1) : null;

        return new ArrivalCheckResponse(
                arrived,
                remaining,
                null,
                null,
                nextLegIndex,
                null,
                null,
                null,
                false
        );
    }
}
