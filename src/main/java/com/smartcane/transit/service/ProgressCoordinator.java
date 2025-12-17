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
import com.smartcane.transit.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 추가된 서비스 Import
import com.smartcane.transit.service.BusStationService.PublicStationInfo;
import com.smartcane.transit.service.RealTimeBusService.BusArrivalInfo;

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

    // ✅ [신규] 실시간 정보 조회를 위한 서비스 주입
    private final BusStationService busStationService;
    private final RealTimeBusService realTimeBusService;

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
        // (상태 저장 및 노이즈 필터링용으로는 중앙값을 계속 사용)
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
        Double lookAhead = isWalk ? props.getLookAheadWalkM() : null;

        // 클라이언트가 보낸 값 우선
        if (p.arriveRadiusM() != null) {
            arriveRadius = p.arriveRadiusM();
        }
        if (isWalk && p.lookAheadM() != null) {
            lookAhead = p.lookAheadM();
        }

        // 6) ArrivalCheckRequest 생성 (★ 하이브리드 방식 적용 ★)
        // 도착 판정의 민첩성을 위해 보정된 좌표(latMed) 대신 실시간 좌표(p.lat) 사용
        ArrivalCheckRequest areq = new ArrivalCheckRequest(
                p.lat(), p.lon(),
                state.getItineraryIndex(), state.getLegIndex(),
                state.getStepIndex(),
                arriveRadius,
                lookAhead
        );

        // 7) 도착 판정
        ArrivalCheckResponse ares = isWalk
                ? walkArrivalService.evaluate(itinerary, areq)
                : transitArrivalService.evaluate(itinerary, areq);

        // WALK 일 때는 현재 스텝 인덱스를 매번 TripState에 반영
        if (isWalk && ares.currentStepIndex() != null) {
            state.setStepIndex(ares.currentStepIndex());
        }

        // 7-1) remainingMeters NaN/∞/음수 방어
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

        // step 인덱스 전이 (히스테리시스 이후) — 주로 대중교통용
        if (!isWalk && nextStep != null) {
            state.setStepIndex(nextStep);
        }

        // leg 인덱스 전이
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

            // leg 가 바뀐 경우, 새 leg 의 초기 stepIndex 재계산
            Integer initStep = computeInitialStepIndex(meta, state.getItineraryIndex(), bounded);
            state.setStepIndex(initStep);
        }

        // --------------------------------------------------------------------------------------
        // 8-1) [신규] 자동 탑승(ONBOARD) 감지 로직 (WAITING_TRANSIT -> ONBOARD)
        // --------------------------------------------------------------------------------------
        String currentPhase = state.getPhase();
        if (!isWalk && TripState.PHASE_WAITING_TRANSIT.equals(currentPhase)) {

            // 1. 속도 체크: 3.0 m/s (약 10.8km/h) 이상이면 버스 출발로 간주
            boolean isMovingFast = (p.speedMps() != null && p.speedMps() > 3.0);

            // 2. 정류장 이탈 체크: 정류장과의 거리가 30m 이상 멀어졌는지?
            boolean isLeftStop = false;
            double distFromStart = 0.0;

            if (currentLeg.start() != null && currentLeg.start().lat() != null && currentLeg.start().lon() != null) {
                // LegDto의 좌표는 이미 Double 타입
                double startLat = currentLeg.start().lat();
                double startLon = currentLeg.start().lon();

                // 반응성을 위해 실시간 좌표(p.lat) 사용
                distFromStart = GeoUtils.haversine(p.lat(), p.lon(), startLat, startLon);
                isLeftStop = (distFromStart > 30.0);
            }

            // 3. 탑승 조건 만족 시 상태 전환
            if (isMovingFast && isLeftStop) {
                state.setPhase(TripState.PHASE_ONBOARD);
                state.setArrivalStreak(0); // 탑승했으므로 도착 스트릭 초기화
                log.info("[StateChange] 대기 종료 -> 탑승(ONBOARD) 자동 감지! (Speed: {}m/s, Distance: {}m)",
                        p.speedMps(), distFromStart);
            }
        }
        // --------------------------------------------------------------------------------------

        // =====================================================================
        // 9) [수정] Phase 업데이트 로직 (버그 수정됨)
        // =====================================================================

        // 1. 위 로직들을 거치며 legIndex가 변했을 수 있으므로, 현재 상태의 Leg 정보를 다시 가져옵니다.
        SkTransitRootDto.LegDto currentLegNow = itinerary.legs().get(state.getLegIndex());
        String currentMode = (currentLegNow.mode() != null) ? currentLegNow.mode() : "WALK";

        // 2. '현재' 걷는 구간인지 확인합니다.
        boolean isWalkNow = "WALK".equals(currentMode);

        if (isWalkNow) {
            // 걷는 구간이면 확실하게 WALKING 상태
            state.setPhase(TripState.PHASE_WALKING);
        } else {
            // 대중교통(버스/지하철) 구간
            String phase = state.getPhase();

            // (1) 상태가 비어있거나,
            // (2) 대중교통 구간인데 'WALKING'으로 잘못 남아있는 경우 (이전 구간의 잔재)
            // ==> 'ONBOARD'로 자동 보정
            if (phase == null || phase.isBlank() || TripState.PHASE_WALKING.equals(phase)) {

                // ⚠️ 단, 방금 8번 로직에서 'WAITING_TRANSIT'으로 설정했다면 건드리지 않아야 합니다.
                if (!TripState.PHASE_WAITING_TRANSIT.equals(phase)) {
                    state.setPhase(TripState.PHASE_ONBOARD);
                }
            }
            // 그 외(WAITING_TRANSIT, ONBOARD, TRANSFER 등)는 기존 값 유지
        }

        // 10) 최근 업링크 시각/좌표 업데이트
        long now = (p.timestampEpochMs() != null) ? p.timestampEpochMs() : System.currentTimeMillis();
        state.setLastLon(p.lon());
        state.setLastLat(p.lat());
        state.setLastTs(now);

        tripStore.save(tripId, state);

        // =================================================================
        // [신규] 실시간 도착 정보 조회 및 TTS 보강 로직 (WAITING_TRANSIT 일 때만)
        // =================================================================
        String additionalTts = "";

        // 현재 상태가 '대기 중'인지 다시 확인 (9번 로직 이후 최종 상태 기준)
        if (!isWalkNow && TripState.PHASE_WAITING_TRANSIT.equals(state.getPhase())) {

            // 1. SK API에서 타야 할 버스 정보("간선:매월26")와 정류장 좌표 확인
            String skRouteNameFull = (currentLegNow.route() != null) ? currentLegNow.route() : "";
            // 파싱: "간선:매월26" -> "매월26"
            String targetRouteNo = skRouteNameFull.contains(":")
                    ? skRouteNameFull.substring(skRouteNameFull.indexOf(":") + 1)
                    : skRouteNameFull;

            Double startLat = (currentLegNow.start() != null) ? currentLegNow.start().lat() : null;
            Double startLon = (currentLegNow.start() != null) ? currentLegNow.start().lon() : null;

            if (startLat != null && startLon != null && !targetRouteNo.isBlank()) {
                try {
                    // 2. 좌표로 공공데이터 정류장 ID & CityCode 찾기 (Blocking 호출)
                    PublicStationInfo stationInfo = busStationService.findNearestStation(startLat, startLon).block();

                    if (stationInfo != null) {
                        // 3. 정류장 ID + 노선명("매월26")으로 도착 정보 조회 (Blocking 호출)
                        BusArrivalInfo arrivalInfo = realTimeBusService
                                .getArrivalInfo(stationInfo.cityCode(), stationInfo.nodeId(), targetRouteNo)
                                .block();

                        if (arrivalInfo != null) {
                            // 4. TTS 메시지 생성 (시간 정보 포함 로직 추가)
                            StringBuilder sb = new StringBuilder();
                            sb.append(String.format(" 현재 %s 버스는", targetRouteNo));

                            // (1) 남은 정거장 수 안내
                            if (arrivalInfo.stopsLeft() != null) {
                                sb.append(String.format(" %d정거장 전", arrivalInfo.stopsLeft()));
                            } else if (arrivalInfo.message() != null) {
                                // 정거장 수 정보가 없으면 '전전' 같은 메시지 사용
                                sb.append(String.format(" %s 위치", arrivalInfo.message()));
                            }

                            // (2) 남은 시간(분) 안내 - 핵심 추가 사항 ✨
                            if (arrivalInfo.secondsLeft() != null) {
                                int minutes = arrivalInfo.secondsLeft() / 60;
                                if (minutes > 0) {
                                    sb.append(String.format(", 약 %d분 후 도착합니다.", minutes));
                                } else {
                                    sb.append(", 곧 도착합니다.");
                                }
                            } else {
                                sb.append("입니다.");
                            }

                            additionalTts = sb.toString();
                        }
                    }
                } catch (Exception e) {
                    log.warn("실시간 버스 정보 조회 실패 (무시함): {}", e.getMessage());
                }
            }
        }
        // =================================================================

        // 11) 안내 문구 생성
        String tts = guidanceTextGenerator.from(ares, state, itinerary, currentLeg);

        // ✅ 실시간 정보가 있으면 TTS 뒤에 붙여줌
        if (!additionalTts.isBlank()) {
            tts += additionalTts;
        }

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