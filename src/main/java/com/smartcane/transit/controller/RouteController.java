package com.smartcane.transit.controller;

import com.smartcane.transit.dto.request.*;
import com.smartcane.transit.dto.response.*;
import com.smartcane.transit.service.RouteProgressService;
import com.smartcane.transit.service.RouteService;
import com.smartcane.transit.service.TripState;
import com.smartcane.transit.service.TripStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/transit") // ✅ 초기 설계에 맞춘 베이스 경로
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteProgressService progressService;
    private final TripStore tripStore; // 상태 조회용 (InMemoryTripStore → 이후 Redis 교체)

    /**
     * POST /api/transit/plan
     * - 경로 생성: SK API 호출 결과(MetaData) + 서버 발급 tripId 반환
     */
    @PostMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RoutePlanInitResponse> plan(@RequestBody RoutePlanRequest query) {
        String tripId = UUID.randomUUID().toString(); // 서버 발급(임시). 필요시 RouteService에서 발급해도 무방.
        return routeService.searchRoutes(query)
                .map(tr -> {
                    // 초기 상태(0,0,0,"WALKING")로 저장(선택)
                    tripStore.init(tripId, 0, 0, 0, "WALKING");
                    return new RoutePlanInitResponse(tripId, tr.metaData());
                });
    }

    /**
     * POST /api/transit/trips/{tripId}/progress
     * - 진행상황 업링크: iOS 현재 위치/센서 → 안내/다음 타겟 응답
     * - Redis 붙기 전까지는 ProgressUpdateEnvelope(metaData, progress)를 받는다.
     */
    @PostMapping("/trips/{tripId}/progress")
    public GuidanceResponse progress(@PathVariable String tripId,
                                     @RequestBody ProgressUpdateEnvelope req) {
        return progressService.updateProgress(tripId, req);
    }

    /**
     * GET /api/transit/trips/{tripId}
     * - 현재 Trip 상태 조회(디버깅/복구용)
     */
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
    @PostMapping("/trips/{tripId}/event")
    public ResponseEntity<Void> pushEvent(@PathVariable String tripId,
                                          @RequestBody TripEventRequest event) {
        TripState state = tripStore.load(tripId);
        if (state == null) return ResponseEntity.notFound().build();

        // 간단한 상태 전이(예시). 실제 전이는 RouteProgressService로 이동 가능.
        switch (event.type()) {
            case "BOARD" -> state.setPhase("ONBOARD");
            case "ALIGHT" -> state.setPhase("TRANSFER");
            case "TRANSFER_CONFIRMED" -> state.setPhase("WALKING");
            case "ARRIVED" -> state.setPhase("ARRIVED");
            case "CANCEL" -> state.setPhase("CANCELLED");
            default -> { /* no-op */ }
        }
        tripStore.save(tripId, state);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/transit/stops/nearby
     * - 반경 내 정류장/역 검색(보조)
     * - 현재는 스텁: 추후 SK 주변정류장/역 조회 API 연동
     */
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
    @PostMapping(value = "/_legacy/plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TransitResponse> getRoutesLegacy(@RequestBody RoutePlanRequest query) {
        return routeService.searchRoutes(query);
    }

    /**
     * 기존: 도착 체크(보행)
     * 지금은 내부적으로 progress 로직에 통합되는 방향 권장.
     * 하위호환 필요 없으면 제거해도 됨.
     */
    @PostMapping("/_legacy/arrival/walk")
    public ResponseEntity<ArrivalCheckResponse> checkWalkArrivalLegacy(
            @RequestBody MetaData body,
            @RequestParam double currLat,
            @RequestParam double currLon,
            @RequestParam(defaultValue = "0") int itineraryIndex,
            @RequestParam int legIndex,
            @RequestParam(required = false) Integer stepIndex,
            @RequestParam(defaultValue = "12") double arriveRadiusM,
            @RequestParam(required = false) Double lookAheadM
    ) {
        var req = new ArrivalCheckRequest(currLat, currLon, itineraryIndex, legIndex, stepIndex, arriveRadiusM, lookAheadM);
        var itin = body.plan().itineraries().get(itineraryIndex);
        var res = progressService.checkWalkStep(itin, req);
        return ResponseEntity.ok(res);
    }

    /**
     * 기존: 도착 체크(대중교통)
     */
    @PostMapping("/_legacy/arrival/transit")
    public ResponseEntity<ArrivalCheckResponse> checkTransitArrivalLegacy(
            @RequestBody MetaData body,
            @RequestParam double currLat,
            @RequestParam double currLon,
            @RequestParam(defaultValue = "0") int itineraryIndex,
            @RequestParam int legIndex,
            @RequestParam(defaultValue = "20") double arriveRadiusM
    ) {
        var req = new ArrivalCheckRequest(currLat, currLon, itineraryIndex, legIndex, null, arriveRadiusM, null);
        var itin = body.plan().itineraries().get(itineraryIndex);
        var res = progressService.checkTransitLeg(itin, req);
        return ResponseEntity.ok(res);
    }
}
