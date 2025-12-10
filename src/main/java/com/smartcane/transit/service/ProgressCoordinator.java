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
import com.smartcane.transit.util.GeoUtils; // ✅ 거리 계산용 유틸 Import 확인
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

        // 3) 속도 게이팅 및 중앙값 필터링 (안정적인 좌표 보정용 - 상태 저장 및 표시에 사용)
        if (p.speedMps() != null && p.speedMps() < props.getMinSpeedMps()) {
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        } else {
            pushWithCap(state.getLatBuf(), p.lat(), props.getMedianWindow());
            pushWithCap(state.getLonBuf(), p.lon(), props.getMedianWindow());
        }

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

        // 5) 모드별 파라미터 선택
        String modeRaw = currentLeg.mode() != null ? currentLeg.mode() : "WALK";
        String mode = modeRaw.toUpperCase();
        boolean isWalk = "WALK".equals(mode);

        // WALK leg 이고 stepIndex 가 비어 있으면 0으로 초기화
        if (isWalk && state.getStepIndex() == null) {
            if (currentLeg.steps() != null && !currentLeg.steps().isEmpty()) {
                state.setStepIndex(0);
                log.info("[PROGRESS] WALK leg 이고 stepIndex 가 null 이라 0으로 초기화했습니다.");
            }
        }

        double arriveRadius = isWalk ? props.getArriveRadiusWalkM() : props.getArriveRadiusTransitM();
        Double lookAhead = isWalk ? props.getLookAheadWalkM() : null;

        if (p.arriveRadiusM() != null) {
            arriveRadius = p.arriveRadiusM();
        }
        if (isWalk && p.lookAheadM() != null) {
            lookAhead = p.lookAheadM();
        }

        // 6) ArrivalCheckRequest 생성 (★ 하이브리드 적용: 실시간 좌표 사용 ★)
        // 도착 판정에는 중앙값(latMed)보다 실시간 좌표(p.lat)를 사용하여
        // 버스 출발 시 정류장 이탈이나 도착을 즉각적으로 감지하도록 함.
        ArrivalCheckRequest areq = new ArrivalCheckRequest(
                p.lat(), p.lon(),  // 👈 latMed, lonMed 대신 Raw 좌표 사용
                state.getItineraryIndex(), state.getLegIndex(),
                state.getStepIndex(),
                arriveRadius,
                lookAhead
        );

        // 7) 도착 판정
        ArrivalCheckResponse ares = isWalk
                ? walkArrivalService.evaluate(itinerary, areq)
                : transitArrivalService.evaluate(itinerary, areq);

        if (isWalk && ares.currentStepIndex() != null) {
            state.setStepIndex(ares.currentStepIndex());
        }

        double remRaw = ares.remainingMeters();
        double remSafe;
        if (Double.isNaN(remRaw) || Double.isInfinite(remRaw) || remRaw < 0) {
            remSafe = 9999.0;
            log.warn("[PROGRESS] remainingMeters invalid. raw={}, tripId={}, legIdx={}", remRaw, tripId, state.getLegIndex());
        } else {
            remSafe = remRaw;
        }

        log.info(
                "[PROGRESS] tripId={} legIdx={} latRaw={} lonRaw={} remSafe={} arrived={}",
                tripId, state.getLegIndex(), p.lat(), p.lon(), remSafe, ares.arrived()
        );

        // 8) 히스테리시스: 연속 N번 도착이어야 진짜 도착
        if (ares.arrived()) {
            state.setArrivalStreak(state.getArrivalStreak() + 1);
        } else {
            state.setArrivalStreak(0);
        }
        boolean arrivedStable = state.getArrivalStreak() >= props.getArrivalHysteresisN();

        Integer nextLeg = arrivedStable ? ares.nextLegIndex() : null;
        Integer nextStep = arrivedStable ? ares.nextStepIndex() : null;

        // step 전이 (BUS/SUBWAY)
        if (!isWalk && nextStep != null) {
            state.setStepIndex(nextStep);
        }

        // leg 전이 (WALK -> BUS 대기 전환 로직 포함)
        if (nextLeg != null) {
            int bounded = Math.min(nextLeg, Math.max(0, itinerary.legs().size() - 1));
            state.setLegIndex(bounded);

            // 1️⃣ 바뀐 Leg가 어떤 모드인지 확인
            SkTransitRootDto.LegDto newLeg = itinerary.legs().get(bounded);
            String newMode = (newLeg.mode() != null) ? newLeg.mode() : "WALK";

            // 2️⃣ 대중교통이면 "WAITING_TRANSIT", 도보면 "WALKING"으로 상태 변경
            if ("BUS".equals(newMode) || "SUBWAY".equals(newMode)) {
                state.setPhase(TripState.PHASE_WAITING_TRANSIT);
                log.info("[StateChange] 보행 종료 -> 대중교통 대기 상태로 전환 (WAITING_TRANSIT)");
            } else {
                state.setPhase(TripState.PHASE_WALKING);
            }

            Integer initStep = computeInitialStepIndex(meta, state.getItineraryIndex(), bounded);
            state.setStepIndex(initStep);
        }

        // --------------------------------------------------------------------------------------
        // 8-1) 자동 탑승(ONBOARD) 감지 로직 (WAITING_TRANSIT -> ONBOARD)
        // --------------------------------------------------------------------------------------
        String currentPhase = state.getPhase();
        if (!isWalk && TripState.PHASE_WAITING_TRANSIT.equals(currentPhase)) {

            // 1. 속도 체크 (3.0 m/s 이상)
            boolean isMovingFast = (p.speedMps() != null && p.speedMps() > 3.0);

            // 2. 정류장 이탈 체크 (30m 이상) - 여기서도 실시간 좌표(p.lat)를 사용하여 즉시 감지
            boolean isLeftStop = false;
            double distFromStart = 0.0;

            if (currentLeg.start() != null && currentLeg.start().lat() != null && currentLeg.start().lon() != null) {
                double startLat = currentLeg.start().lat(); // 이미 Double
                double startLon = currentLeg.start().lon(); // 이미 Double

                // 실시간 좌표 사용
                distFromStart = GeoUtils.haversine(p.lat(), p.lon(), startLat, startLon);
                isLeftStop = (distFromStart > 30.0);
            }

            // 3. 상태 전환
            if (isMovingFast && isLeftStop) {
                state.setPhase(TripState.PHASE_ONBOARD);
                state.setArrivalStreak(0);
                log.info("[StateChange] 대기 종료 -> 탑승(ONBOARD) 자동 감지! (Speed: {}m/s, Distance: {}m)",
                        p.speedMps(), distFromStart);
            }
        }

        // [수정] 9) Phase 업데이트 로직 수정


        // 🚨 중요: 위에서 legIndex가 변경되었을 수 있으므로, 현재 Leg 모드를 다시 확인해야 합니다.
        SkTransitRootDto.LegDto currentLegNow = itinerary.legs().get(state.getLegIndex());
        String currentMode = (currentLegNow.mode() != null) ? currentLegNow.mode() : "WALK";
        boolean isWalkNow = "WALK".equals(currentMode); // 👈 변수명 변경 (isWalk -> isWalkNow)

        if (isWalkNow) {
            state.setPhase(TripState.PHASE_WALKING);
        } else {
            // 대중교통 구간임
            String phase = state.getPhase();

            // 1. 상태가 비어있거나,
            // 2. 대중교통 구간인데 'WALKING'으로 잘못 남아있는 경우 (이전 상태 잔재)
            // -> 'ONBOARD'로 자동 보정
            if (phase == null || phase.isBlank() || TripState.PHASE_WALKING.equals(phase)) {

                // 단, 방금 8번 로직에서 'WAITING_TRANSIT'으로 설정했다면 건드리지 않음!
                if (!TripState.PHASE_WAITING_TRANSIT.equals(phase)) {
                    state.setPhase(TripState.PHASE_ONBOARD);
                }
            }
            // 그 외(WAITING_TRANSIT, TRANSFER 등)는 기존 값 유지
        }
        // 10) 최근 업링크 시각/좌표 업데이트
        long now = (p.timestampEpochMs() != null) ? p.timestampEpochMs() : System.currentTimeMillis();
        state.setLastLon(p.lon());
        state.setLastLat(p.lat());
        state.setLastTs(now);

        tripStore.save(tripId, state);

        // 11) 안내 문구 생성
        String tts = guidanceTextGenerator.from(ares, state, itinerary, currentLeg);

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