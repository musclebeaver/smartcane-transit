// service/RouteService.java
package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.SkTransitRootDto;
import com.smartcane.transit.dto.request.RoutePlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final WebClient skTransitWebClient;

    // 우선순위 선택 로직 (stateless라 new로 둬도 되고 @Bean으로 빼도 됨)
    private final SkRouteSelector routeSelector = new SkRouteSelector();

    /**
     * 1) SK 길찾기 원본 호출 (필터링 X, 그대로 받고 싶을 때 사용)
     */
    public Mono<SkTransitRootDto> searchRawRoutes(RoutePlanRequest query) {
        return skTransitWebClient.post()
                .uri("/transit/routes/")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(SkTransitRootDto.class)
                .doOnError(WebClientResponseException.class, ex -> {
                    log.error("SK Transit API 오류: status={}, body={}",
                            ex.getStatusCode(), ex.getResponseBodyAsString());
                });
    }

    /**
     * 2) 버스 위주 → 없으면 지하철+버스 위주로 필터링된 결과
     *    /plan 엔드포인트에서 이 메서드를 사용하면 됨.
     */
    public Mono<SkTransitRootDto> searchPreferredRoutes(RoutePlanRequest query) {
        return searchRawRoutes(query)
                .map(raw -> {
                    // metaData 혹시 null일 때 방어 코드
                    SkTransitRootDto.MetaDataDto meta = raw.metaData();
                    if (meta == null || meta.plan() == null || meta.plan().itineraries() == null) {
                        // 응답 구조가 비정상일 땐 그냥 raw 그대로 반환
                        return raw;
                    }

                    List<SkTransitRootDto.ItineraryDto> all = meta.plan().itineraries();

                    // 버스 우선 / 지하철+버스 우선 적용
                    List<SkTransitRootDto.ItineraryDto> selected = routeSelector.selectPreferredItineraries(all);

                    // 새 PlanDto / MetaDataDto 재조립
                    SkTransitRootDto.PlanDto filteredPlan =
                            new SkTransitRootDto.PlanDto(selected);

                    SkTransitRootDto.MetaDataDto filteredMeta =
                            new SkTransitRootDto.MetaDataDto(
                                    meta.requestParameters(),  // 기존 요청 파라미터 유지
                                    filteredPlan               // 경로만 우리가 선택한 걸로 교체
                            );

                    // 최종 Root DTO
                    return new SkTransitRootDto(filteredMeta);
                });
    }

    /**
     * 3) 기존 메서드 이름을 그대로 쓰고 싶다면,
     *    /plan 쪽에서 버스 우선 로직을 쓰게 하려고 searchRoutes를 래핑해도 됨.
     *
     *    Controller에서 그냥 routeService.searchRoutes(...)만 호출하면
     *    자동으로 버스 우선 경로가 적용되게.
     */
    public Mono<SkTransitRootDto> searchRoutes(RoutePlanRequest query) {
        // 지금부터는 "우선순위 적용된 결과"가 기본 행동
        return searchPreferredRoutes(query);
    }
}
