package com.smartcane.transit.controller;

import com.smartcane.transit.dto.request.*;
import com.smartcane.transit.dto.response.*;
import com.smartcane.transit.service.RouteProgressService;
import com.smartcane.transit.service.RouteService;
import com.smartcane.transit.service.TripState;
import com.smartcane.transit.service.TripStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Tag(name = "실시간 길안내", description = "길찾기 계획 수립 및 진행 상태 업데이트 API")
@RestController
@RequestMapping("/api/transit") // ✅ 초기 설계에 맞춘 베이스 경로
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteProgressService progressService;
    private final TripStore tripStore; // 상태 조회용 (InMemoryTripStore → 이후 Redis 교체)


    /**
     * POST /api/transit/plan
     *
     * SK 길찾기 호출 + 서버 tripId 발급.
     *
     * - RouteService.searchRoutes()
     *   → SK 응답(itineraries) 중
     *      1순위: 버스 위주(pathType = 2)
     *      2순위: 지하철+버스(pathType = 3)
     *      로 필터링된 SkTransitRootDto 를 반환
     *
     * - 여기서는 그 중 MetaData만 iOS에게 내려주고,
     *   서버 측에는 tripId 기준으로 진행 상태(phase 등)를 TripStore에 초기화.
     */
    @Operation(
            summary = "길찾기 계획 수립",
            description = """
        SK 길찾기 API를 호출해 경로를 조회하고, 서버에서 tripId를 발급합니다.
        iOS/테스트 클라이언트에서는 이 tripId를 저장해 두었다가 이후 progress API 호출 시 사용합니다.
        """
    )
    @PostMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RoutePlanInitResponse> plan(@RequestBody RoutePlanRequest query) {
        String tripId = UUID.randomUUID().toString(); // 서버 발급 tripId

        return routeService.searchRoutes(query)      // Mono<SkTransitRootDto>
                .map((SkTransitRootDto root) -> {
                    // ✅ 이 시점의 root.metaData().plan().itineraries() 는
                    //    이미 "버스 우선 → 지하철+버스" 로 필터된 상태
                    SkTransitRootDto.MetaDataDto meta = root.metaData();

                    // 초기 Trip 상태 등록 (보행 시작 기준)
                    tripStore.init(
                            tripId,
                            meta,
                            0,      // itineraryIndex
                            0,      // legIndex
                            null,   // stepIndex
                            TripState.PHASE_WALKING
                    );

                    // iOS 에게는 tripId + MetaData 만 내려줌
                    return new RoutePlanInitResponse(tripId, meta);
                });
    }

    /**
     * POST /api/transit/trips/{tripId}/progress
     * - 진행상황 업링크: iOS 현재 위치/센서 → 안내/다음 타겟 응답
     * - Redis 붙기 전까지는 ProgressUpdateEnvelope(metaData, progress)를 받는다.
     */

    @Operation(
            summary = "진행 상황 업로드",
            description = "현재 위치/센서 데이터를 업로드하면 다음 안내(다음 타겟, 상태) 정보를 응답합니다."
    )
    @PostMapping("/trips/{tripId}/progress")
    public GuidanceResponse progress(@PathVariable String tripId,
                                     @RequestBody ProgressUpdateEnvelope req) {
        return progressService.updateProgress(tripId, req);
    }

    /**
     * GET /api/transit/trips/{tripId}
     * - 현재 Trip 상태 조회(디버깅/복구용)
     */
    @Operation(
            summary = "Trip 상태 조회",
            description = "서버에 저장된 TripState를 조회합니다. 디버깅/복구용."
    )
    @GetMapping("/trips/{tripId}")
    public ResponseEntity<TripState> getTrip(@PathVariable String tripId) {
        TripState state = tripStore.load(tripId);
        return (state != null) ? ResponseEntity.ok(state) : ResponseEntity.notFound().build();
    }

    /**
     * POST /api/transit/trips/{tripId}/event
     * - (옵션) 승/하차/환승 확정 이벤트 업링크
     * - 최소 구현: phase 업데이트 정도만 처리(향후 고도화)
     */
    @Operation(
            summary = "Trip 이벤트 전송",
            description = "BOARD, ALIGHT, TRANSFER_CONFIRMED, ARRIVED, CANCEL 등의 이벤트로 Trip 상태를 갱신합니다."
    )
    @PostMapping("/trips/{tripId}/event")
    public ResponseEntity<Void> pushEvent(@PathVariable String tripId,
                                          @RequestBody TripEventRequest event) {
        TripState state = tripStore.load(tripId);
        if (state == null) return ResponseEntity.notFound().build();

        String type = event.type(); // "BOARD" / "ALIGHT" / "TRANSFER_CONFIRMED" / "ARRIVED" / "CANCEL"

        switch (type) {
            case "BOARD" -> {
                // 버스/지하철 탑승 완료
                state.setPhase(TripState.PHASE_ONBOARD);
                // 필요하면 여기서 arrivalStreak 리셋 등도 가능
                state.setArrivalStreak(0);
            }
            case "ALIGHT" -> {
                // 버스/지하철 하차 완료 → 보통 환승 or 최종 보행 준비 상태
                state.setPhase(TripState.PHASE_TRANSFER);
                state.setArrivalStreak(0);
            }
            case "TRANSFER_CONFIRMED" -> {
                // 사용자가 "이제 다음 구간 보행 시작" 같은 제스처를 했다고 가정
                state.setPhase(TripState.PHASE_WALKING);
                state.setArrivalStreak(0);
            }
            case "ARRIVED" -> {
                // 사용자가 "도착 맞음"을 눌렀다거나, 앱에서 강제 종료 전에 마지막 상태 저장
                state.setPhase(TripState.PHASE_ARRIVED);
            }
            case "CANCEL" -> {
                state.setPhase(TripState.PHASE_CANCELLED);
            }
            default -> {
                // no-op
            }
        }

        tripStore.save(tripId, state);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/transit/stops/nearby
     * - 반경 내 정류장/역 검색(보조)
     * - 현재는 스텁: 추후 SK 주변정류장/역 조회 API 연동
     */
    @Operation(
            summary = "주변 정류장 조회(Stub)",
            description = "현재는 501을 반환하는 스텁입니다. 추후 SK 주변 정류장 API 연동 예정."
    )
    @GetMapping("/stops/nearby")
    public ResponseEntity<Void> nearbyStops(@RequestParam double lon,
                                            @RequestParam double lat,
                                            @RequestParam(defaultValue = "150") double radiusM) {
        // TODO: SK 주변 정류장/역 검색 API 연동 후 DTO로 응답
        return ResponseEntity.status(501).build(); // Not Implemented
    }

    // ------------------------------
    // (선택) 구 엔드포인트 유지: 하위호환을 위해 남겨둠 (원하면 제거 가능)
    // ------------------------------

    /**
     * 기존: POST /routes
     * 지금은 /api/transit/plan으로 대체됨. 필요시 하위호환 유지.
     */
//    @PostMapping(value = "/_legacy/plan", produces = MediaType.APPLICATION_JSON_VALUE)
//    public Mono<TransitResponse> getRoutesLegacy(@RequestBody RoutePlanRequest query) {
//        return routeService.searchRoutes(query);
//    }

    /**
     * 기존: 도착 체크(보행)
     * 지금은 내부적으로 progress 로직에 통합되는 방향 권장.
     * 하위호환 필요 없으면 제거해도 됨.
     */
//    @PostMapping("/_legacy/arrival/walk")
//    public ResponseEntity<ArrivalCheckResponse> checkWalkArrivalLegacy(
//            @RequestBody MetaData body,
//            @RequestParam double currLat,
//            @RequestParam double currLon,
//            @RequestParam(defaultValue = "0") int itineraryIndex,
//            @RequestParam int legIndex,
//            @RequestParam(required = false) Integer stepIndex,
//            @RequestParam(defaultValue = "12") double arriveRadiusM,
//            @RequestParam(required = false) Double lookAheadM
//    ) {
//        var req = new ArrivalCheckRequest(currLat, currLon, itineraryIndex, legIndex, stepIndex, arriveRadiusM, lookAheadM);
//        var itin = body.plan().itineraries().get(itineraryIndex);
//        var res = progressService.checkWalkStep(itin, req);
//        return ResponseEntity.ok(res);
//    }

    /**
     * 기존: 도착 체크(대중교통)
     */
//    @PostMapping("/_legacy/arrival/transit")
//    public ResponseEntity<ArrivalCheckResponse> checkTransitArrivalLegacy(
//            @RequestBody MetaData body,
//            @RequestParam double currLat,
//            @RequestParam double currLon,
//            @RequestParam(defaultValue = "0") int itineraryIndex,
//            @RequestParam int legIndex,
//            @RequestParam(defaultValue = "20") double arriveRadiusM
//    ) {
//        var req = new ArrivalCheckRequest(currLat, currLon, itineraryIndex, legIndex, null, arriveRadiusM, null);
//        var itin = body.plan().itineraries().get(itineraryIndex);
//        var res = progressService.checkTransitLeg(itin, req);
//        return ResponseEntity.ok(res);
//    }
}
