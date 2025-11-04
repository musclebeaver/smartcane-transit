// service/RouteService.java
package com.smartcane.transit.service;

import com.smartcane.transit.dto.request.RoutePlanRequest;
import com.smartcane.transit.dto.response.TransitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final WebClient skTransitWebClient;

    public Mono<TransitResponse> searchRoutes(RoutePlanRequest query) {
        return skTransitWebClient.post()
                .uri("/transit/routes/")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(TransitResponse.class);
    }
}
