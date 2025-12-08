package com.smartcane.transit.service;

import com.smartcane.transit.config.GuidanceProperties;
import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.request.ProgressUpdateEnvelope;
import com.smartcane.transit.dto.request.ProgressUpdateRequest;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.GuidanceResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import com.smartcane.transit.service.arrival.TransitArrivalService;
import com.smartcane.transit.service.arrival.WalkArrivalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진행 업데이트의 오케스트레이션 레이어.
 * - TripState 로드/초기화/저장
 * - 현재 Leg의 모드에 따라 적절한 도착판정 서비스(Walk/Transit) 호출
 * - ArrivalCheckResponse를 기반으로 상태 전이 및 TTS 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressCoordinator {

    private final TripStore tripStore;
    private final GuidanceTextGenerator guidanceTextGenerator;
    private final WalkArrivalService walkArrivalService;
    private final TransitArrivalService transitArrivalService;
    private final GuidanceProperties props;

    /** 보행 구간 판정(테스트/디버깅용 공개) */
    public ArrivalCheckResponse checkWalkStep(SkTransitRootDto.ItineraryDto itin,
                                              ArrivalCheckRequest req) {
        return walkArrivalService.evaluate(itin, req);
    }

    /** 대중교통 구간 판정(테스트/디버깅용 공개) */
    public ArrivalCheckResponse checkTransitLeg(SkTransitRootDto.ItineraryDto itin,
                                                ArrivalCheckRequest req) {
        return transitArrivalService.evaluate(itin, req);
    }

    // 중앙값 계산 유틸
    private static double median(java.util.Deque<Double> dq) {
        if (dq.isEmpty()) return Double.NaN;
        var arr = dq.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = arr.length;
        return (n % 2 == 1) ? arr[n / 2] : (arr[n / 2 - 1] + arr[n / 2]) / 2.0;
    }

    private static void pushWithCap(java.util.Deque<Double> dq, double v, int cap) {
        dq.addLast(v);
        while (dq.size() > cap) dq.removeFirst();
    }

    /**
     * 초기 stepIndex 계산:
     * - 현재 itIdx/legIdx 가 WALK 이고 steps 가 1개 이상이면 0부터 시작
     * - 아니면 null 유지
     */
    private Integer computeInitialStepIndex(SkTransitRootDto.MetaDataDto meta,
                                            int itIdx,
                                            int legIdx) {
        if (meta == null || meta.plan() == null || meta.plan().itineraries() == null) {
            return null;
        }
        var itineraries = meta.plan().itineraries();
        if (itineraries.isEmpty() || itIdx < 0 || itIdx >= itineraries.size()) {
            return null;
        }
        var itin = itineraries.get(itIdx);
        if (itin == null || itin.legs() == null || itin.legs().isEmpty()) {
            return null;
        }
        if (legIdx < 0 || legIdx >= itin.legs().size()) {
            return null;
        }
        var leg = itin.legs().get(legIdx);

        String modeRaw = (leg.mode() != null) ? leg.mode() : "WALK";
        String mode = modeRaw.toUpperCase();

        if (!"WALK".equals(mode)) {
            return null;
        }
        if (leg.steps() == null || leg.steps().isEmpty()) {
            return null;
        }

        // WALK + steps 존재 → 첫 번째 step부터 시작
        return 0;
    }

    /**
     * iOS 진행 업링크 처리:
     * - Envelope(metaData, progress) 수신 → 상태 로드 → 도착판정 → 상태전이 → TTS → 응답
     */
    public GuidanceResponse updateProgress(String tripId, ProgressUpdateEnvelope envelope) {

        if (envelope == null || envelope.progress() == null) {
            throw new IllegalArgumentException("progress 가 비어 있습니다.");
        }

        // 1) 진행 정보
        ProgressUpdateRequest p = envelope.progress();

        // 2) TripStore 에서 meta 로드 (기본 경로)
        SkTransitRootDto.MetaDataDto meta = tripStore.loadMeta(tripId);

        // 2-1) 혹시 meta 가 없다면, envelope.metaData() 로 초기화 시도 (옵션)
        if (meta == null && envelope.metaData() != null) {
            meta = envelope.metaData();

            // meta 기반으로 첫 it=0, leg=0 에 대해 초기 stepIndex 계산
            Integer initStep = computeInitialStepIndex(meta, 0, 0);

            // meta + 초기 상태 저장 (WALKING, 0,0,initStep)
            tripStore.init(tripId, meta, 0, 0, initStep, TripState.PHASE_WALKING);
        }

        // 2-2) 그래도 meta 없으면 /plan 부터 다시 하라는 에러
        if (meta == null) {
            throw new IllegalStateException(
                    "메타데이터가 없습니다. /api/transit/plan 을 먼저 호출하세요. tripId=" + tripId
            );
        }

        // 3) TripState 로드/초기화
        TripState state = tripStore.load(tripId);
        if (state == null) {
            Integer initStep = computeInitialStepIndex(meta, 0, 0);
            state = new TripState(tripId, 0, 0, initStep, TripState.PHASE_WALKING);
            tripStore.save(tripId, state);
        }

        // 3) 속도 게이팅: 너무 느리면(정지/튐) 샘플 반영을 보수적으로
        if (p.speedMps() != null && p.speedMps() < props.getMinSpeedMps()) {
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        } else {
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        }

        // 3) 중앙값 좌표로 판정 수행
        double latMed = median(state.getLatBuf());
        double lonMed = median(state.getLonBuf());
        if (Double.isNaN(latMed) || Double.isNaN(lonMed)) {
            latMed = p.lat();
            lonMed = p.lon();
        }

        // 4) 현재 Itinerary / Leg 인덱스 보정
        var itineraries = meta.plan().itineraries();
        if (state.getItineraryIndex() < 0 || state.getItineraryIndex() >= itineraries.size()) {
            state.setItineraryIndex(0);
        }
        SkTransitRootDto.ItineraryDto itinerary = itineraries.get(state.getItineraryIndex());

        if (state.getLegIndex() < 0 || state.getLegIndex() >= itinerary.legs().size()) {
            state.setLegIndex(0);
        }
        SkTransitRootDto.LegDto currentLeg = itinerary.legs().get(state.getLegIndex());

        // 5) 모드별 파라미터 선택 (문자열 기반: "WALK" / "BUS" / "SUBWAY")
        String modeRaw = currentLeg.mode() != null ? currentLeg.mode() : "WALK";
        String mode = modeRaw.toUpperCase();
        boolean isWalk = "WALK".equals(mode);

        // WALK leg 이고 stepIndex 가 비어 있으면 0으로 초기화 (steps 존재 시)
        if (isWalk && state.getStepIndex() == null) {
            if (currentLeg.steps() != null && !currentLeg.steps().isEmpty()) {
                state.setStepIndex(0);
                log.info("[PROGRESS] WALK leg 이고 stepIndex 가 null 이라 0으로 초기화했습니다.");
            }
        }

        double arriveRadius = isWalk ? props.getArriveRadiusWalkM() : props.getArriveRadiusTransitM();
        Double lookAhead = isWalk ? props.getLookAheadWalkM() : null; // WalkArrivalService 에서는 현재 사용 안 해도 됨

        // 클라이언트가 보낸 값 우선
        if (p.arriveRadiusM() != null) {
            arriveRadius = p.arriveRadiusM();
        }
        if (isWalk && p.lookAheadM() != null) {
            lookAhead = p.lookAheadM();
        }

        // 6) ArrivalCheckRequest 생성 (중앙값 좌표 사용)
        ArrivalCheckRequest areq = new ArrivalCheckRequest(
                latMed, lonMed,
                state.getItineraryIndex(), state.getLegIndex(),
                state.getStepIndex(),
                arriveRadius,
                lookAhead
        );

        // 7) 도착 판정
        ArrivalCheckResponse ares = isWalk
                ? walkArrivalService.evaluate(itinerary, areq)
                : transitArrivalService.evaluate(itinerary, areq);

        // 👇 WALK 일 때는 현재 스텝 인덱스를 매번 TripState에 반영
        if (isWalk && ares.currentStepIndex() != null) {
            state.setStepIndex(ares.currentStepIndex());
        }

        // 7-1) remainingMeters NaN/∞/음수 방어 + 디버그용 로그
        double remRaw = ares.remainingMeters();
        double remSafe;

        if (Double.isNaN(remRaw) || Double.isInfinite(remRaw) || remRaw < 0) {
            remSafe = 9999.0;
            log.warn("[PROGRESS] remainingMeters invalid. raw={}, tripId={}, itIdx={}, legIdx={}, stepIdx={}",
                    remRaw, tripId, state.getItineraryIndex(), state.getLegIndex(), state.getStepIndex());
        } else {
            remSafe = remRaw;
        }

        log.info(
                "[PROGRESS] tripId={} itIdx={} legIdx={} stepIdx={} latMed={} lonMed={} remRaw={} remSafe={} arrived={}",
                tripId,
                state.getItineraryIndex(),
                state.getLegIndex(),
                state.getStepIndex(),
                latMed,
                lonMed,
                remRaw,
                remSafe,
                ares.arrived()
        );

        // 8) 히스테리시스: 연속 N번 도착이어야 진짜 도착(leg/step 전이 모두에 적용)
        if (ares.arrived()) {
            state.setArrivalStreak(state.getArrivalStreak() + 1);
        } else {
            state.setArrivalStreak(0);
        }
        boolean arrivedStable = state.getArrivalStreak() >= props.getArrivalHysteresisN();

        Integer nextLeg = arrivedStable ? ares.nextLegIndex() : null;
        Integer nextStep = arrivedStable ? ares.nextStepIndex() : null;

        // step 인덱스 전이 (히스테리시스 이후) — 주로 대중교통용
        // WALK 에서는 위에서 currentStepIndex 로 이미 매번 업데이트하므로 여기서는 건드리지 않는다.
        if (!isWalk && nextStep != null) {
            state.setStepIndex(nextStep);
        }

        // leg 인덱스 전이 (BUS / SUBWAY / 마지막 WALK step 이후)
        if (nextLeg != null) {
            int bounded = Math.min(nextLeg, Math.max(0, itinerary.legs().size() - 1));
            state.setLegIndex(bounded);

            // leg 가 바뀐 경우, 새 leg 의 초기 stepIndex 재계산
            Integer initStep = computeInitialStepIndex(meta, state.getItineraryIndex(), bounded);
            state.setStepIndex(initStep);
        }

        // 9) phase 업데이트 (이벤트를 존중하는 방향)
        if (isWalk) {
            state.setPhase(TripState.PHASE_WALKING);
        } else {
            String phase = state.getPhase();
            if (phase == null || phase.isBlank()) {
                state.setPhase(TripState.PHASE_ONBOARD);
            }
            // WAITING_TRANSIT / TRANSFER / ONBOARD 등은 이벤트에서 온 값을 그대로 둠
        }

        // 10) 최근 업링크 시각/좌표 업데이트
        long now = (p.timestampEpochMs() != null) ? p.timestampEpochMs() : System.currentTimeMillis();
        state.setLastLon(p.lon());
        state.setLastLat(p.lat());
        state.setLastTs(now);

        tripStore.save(tripId, state);

        // 11) 안내 문구 생성
        // - WalkArrivalService:
        //   · currentInstruction = 현재 step.description
        //   · nextInstruction = "NEXT_STEP:123" 형식 (다음 안내 지점까지 남은 거리)
        //   → GuidanceTextGenerator.from(...) 에서 이를 파싱해서
        //     "다음 안내까지 약 123미터 남았습니다." 등으로 조합
        String tts = guidanceTextGenerator.from(ares, state, itinerary, currentLeg);

        // distanceToTargetM 은 클라이언트용 안전 값(remSafe) 그대로 사용 (목적지까지 남은 거리)
        return new GuidanceResponse(
                tripId,
                state.getItineraryIndex(),
                state.getLegIndex(),
                state.getPhase(),
                tts,
                remSafe,
                null
        );
    }
}
