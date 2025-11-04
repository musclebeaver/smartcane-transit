package com.smartcane.transit.service;

import com.smartcane.transit.config.GuidanceProperties;
import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.request.ProgressUpdateEnvelope;
import com.smartcane.transit.dto.request.ProgressUpdateRequest;
import com.smartcane.transit.dto.response.*;
import com.smartcane.transit.service.arrival.TransitArrivalService;
import com.smartcane.transit.service.arrival.WalkArrivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 진행 업데이트의 오케스트레이션 레이어.
 * - TripState 로드/초기화/저장
 * - 현재 Leg의 모드에 따라 적절한 도착판정 서비스(Walk/Transit) 호출
 * - ArrivalCheckResponse를 기반으로 상태 전이 및 TTS 생성
 */
@Service
@RequiredArgsConstructor
public class ProgressCoordinator {

    private final TripStore tripStore;
    private final GuidanceTextGenerator guidanceTextGenerator;
    private final WalkArrivalService walkArrivalService;
    private final TransitArrivalService transitArrivalService;

    private final GuidanceProperties props; // ✅ 주입

    /** 보행 구간 판정(테스트/디버깅용 공개) */
    public ArrivalCheckResponse checkWalkStep(Itinerary itin, ArrivalCheckRequest req) {
        return walkArrivalService.evaluate(itin, req);
    }

    /** 대중교통 구간 판정(테스트/디버깅용 공개) */
    public ArrivalCheckResponse checkTransitLeg(Itinerary itin, ArrivalCheckRequest req) {
        return transitArrivalService.evaluate(itin, req);
    }


    // 중앙값 계산 유틸
    private static double median(java.util.Deque<Double> dq) {
        if (dq.isEmpty()) return Double.NaN;
        var arr = dq.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = arr.length;
        return (n % 2 == 1) ? arr[n/2] : (arr[n/2-1] + arr[n/2]) / 2.0;
    }

    private static void pushWithCap(java.util.Deque<Double> dq, double v, int cap) {
        dq.addLast(v);
        while (dq.size() > cap) dq.removeFirst();
    }

    /**
     * iOS 진행 업링크 처리:
     * - Envelope(metaData, progress) 수신 → 상태 로드 → 도착판정 → 상태전이 → TTS → 응답
     */
    public GuidanceResponse updateProgress(String tripId, ProgressUpdateEnvelope envelope) {
        MetaData meta = envelope.metaData();
        ProgressUpdateRequest p = envelope.progress();

        // 1) 상태 로드/초기화
        TripState state = tripStore.load(tripId);
        if (state == null) {
            state = new TripState(tripId, 0, 0, null, "WALKING");
            tripStore.init(tripId, 0, 0, null, "WALKING");
        }

        // 2) 속도 게이팅: 너무 느리면(정지/튜는 순간) 샘플 반영을 보수적으로
        if (p.speedMps() != null && p.speedMps() < props.getMinSpeedMps()) {
            // 속도가 너무 작을 때는 버퍼에는 넣되, 판정은 직전 중앙값 기준으로 수행(=상태 변동 둔화)
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        } else {
            // 정상 속도 → 버퍼 업데이트
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        }

        // 3) 중앙값 좌표로 스냅·판정 수행
        double latMed = median(state.getLatBuf());
        double lonMed = median(state.getLonBuf());
        // (안전) 중앙값이 NaN이면 이번 샘플로 대체
        if (Double.isNaN(latMed) || Double.isNaN(lonMed)) { latMed = p.lat(); lonMed = p.lon(); }

        // 4) 현재 Itinerary / Leg
        var itineraries = meta.plan().itineraries();
        if (state.getItineraryIndex() < 0 || state.getItineraryIndex() >= itineraries.size()) state.setItineraryIndex(0);
        Itinerary itinerary = itineraries.get(state.getItineraryIndex());
        if (state.getLegIndex() < 0 || state.getLegIndex() >= itinerary.legs().size()) state.setLegIndex(0);
        Leg currentLeg = itinerary.legs().get(state.getLegIndex());

        // 5) 모드별 파라미터 선택
        boolean isWalk = currentLeg.mode() == Mode.WALK;
        double arriveRadius = isWalk ? props.getArriveRadiusWalkM() : props.getArriveRadiusTransitM();
        Double lookAhead   = isWalk ? props.getLookAheadWalkM() : null;

        // (클라가 보낸 값이 있으면 우선)
        if (p.arriveRadiusM() != null) arriveRadius = p.arriveRadiusM();
        if (isWalk && p.lookAheadM() != null) lookAhead = p.lookAheadM();

        // 6) ArrivalCheckRequest 생성 (중앙값 좌표 사용!)
        var areq = new ArrivalCheckRequest(
                latMed, lonMed,
                state.getItineraryIndex(), state.getLegIndex(),
                state.getStepIndex(),
                arriveRadius, lookAhead
        );

        // 7) 판정 호출
        ArrivalCheckResponse ares = isWalk
                ? walkArrivalService.evaluate(itinerary, areq)
                : transitArrivalService.evaluate(itinerary, areq);

        // 8) 히스테리시스: 도착 조건을 연속 N번 만족해야 “진짜 도착”으로 인정
        if (ares.arrived()) {
            state.setArrivalStreak(state.getArrivalStreak() + 1);
        } else {
            state.setArrivalStreak(0);
        }
        boolean arrivedStable = state.getArrivalStreak() >= props.getArrivalHysteresisN();

        // arrivedStable일 때만 next* 반영(깜빡임 방지)
        Integer nextLeg  = arrivedStable ? ares.nextLegIndex()  : null;
        Integer nextStep = arrivedStable ? ares.nextStepIndex() : null;

        if (nextLeg != null) {
            int bounded = Math.min(nextLeg, Math.max(0, itinerary.legs().size() - 1));
            state.setLegIndex(bounded);
        }
        if (nextStep != null) {
            state.setStepIndex(nextStep);
        }

        // phase 간단 업데이트
        state.setPhase(isWalk ? "WALKING" : "ONBOARD");

        // 최근 업링크 시각/좌표 업데이트 (원본 좌표도 보관)
        long now = (p.timestampEpochMs() != null) ? p.timestampEpochMs() : System.currentTimeMillis();
        state.setLastLon(p.lon());
        state.setLastLat(p.lat());
        state.setLastTs(now);

        tripStore.save(tripId, state);

        // 9) 안내 문구
        String tts = guidanceTextGenerator.from(ares, state, itinerary, currentLeg);

        return new GuidanceResponse(
                tripId,
                state.getItineraryIndex(),
                state.getLegIndex(),
                state.getPhase(),
                tts,
                Math.max(0, ares.remainingMeters()),
                null
        );
    }
}
