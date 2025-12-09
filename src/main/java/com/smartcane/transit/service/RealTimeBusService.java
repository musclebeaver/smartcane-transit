package com.smartcane.transit.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartcane.transit.config.PublicDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeBusService {

    private final PublicDataProperties props;

    // 공공데이터포털은 Key가 이미 인코딩된 상태일 수 있어, 인코딩을 방지하는 설정이 필요할 수 있습니다.
    private WebClient getClient() {
        // [변경] props.baseUrl() -> props.getBaseUrl()
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(props.getBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .baseUrl(props.getBaseUrl()) // [변경] Getter 사용
                .build();
    }

    /**
     * 특정 정류소(cityCode, nodeId)의 특정 노선(routeId) 도착 정보 조회
     */
    public Mono<Integer> getArrivalMinutes(String cityCode, String nodeId, String routeId) {
        return getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getSttnAcctoArvlPrearngeInfoList")
                        .queryParam("serviceKey", props.getServiceKey()) // [변경] Getter 사용
                        .queryParam("cityCode", cityCode)
                        .queryParam("nodeId", nodeId)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(TagoResponse.class)
                .map(response -> findRouteArrival(response, routeId))
                .onErrorResume(e -> {
                    log.error("버스 실시간 정보 조회 실패: {}", e.getMessage());
                    return Mono.empty(); // 실패 시 안내 안 함 (조용히 넘어감)
                });
    }

    private Integer findRouteArrival(TagoResponse response, String targetRouteId) {
        if (response == null || response.response == null ||
                response.response.body == null || response.response.body.items == null) {
            return null;
        }

        // 해당 정류소에 오는 모든 버스 중 내가 탈 노선(targetRouteId) 찾기
        // 주의: routeId 포맷이 SK와 공공데이터가 다를 수 있어 매핑 로직이나 퍼지 매칭이 필요할 수 있습니다.
        // 여기서는 예시로 정확히 일치한다고 가정하거나, routeNo(버스번호)로 찾을 수도 있습니다.
        return response.response.body.items.stream()
                .filter(item -> targetRouteId.equals(item.routeId)) // 또는 item.routeNo.equals("150")
                .findFirst()
                .map(item -> item.arrprevstationcnt) // 남은 정거장 수 or arrtime(초)
                .orElse(null);
    }

    // --- DTO 내부 클래스 (간소화) ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagoResponse(BodyWrapper response) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BodyWrapper(Body body) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(List<Item> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(
            String routeId,           // 노선 ID
            String routeNo,           // 노선 번호 (예: 150)
            Integer arrprevstationcnt,// 남은 정거장 수
            Integer arrtime,          // 도착 예정 시간(초)
            String nodeid             // 정류소 ID
    ) {}
}