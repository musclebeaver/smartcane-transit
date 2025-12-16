package com.smartcane.transit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartcane.transit.config.PublicDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeBusService {

    private final PublicDataProperties props;

    private WebClient getClient() {
        // ✅ props.getBaseUrl() -> props.getArrivalBaseUrl() 로 변경
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(props.getArrivalBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .baseUrl(props.getArrivalBaseUrl())
                .build();
    }

    /**
     * 정류소(NodeId)에 도착하는 특정 노선(RouteName)의 정보 조회
     * 예: targetRouteName = "매월26"
     */
    public Mono<BusArrivalInfo> getArrivalInfo(String cityCode, String nodeId, String targetRouteName) {
        return getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getSttnAcctoArvlPrearngeInfoList") // 정류소별 도착정보 조회
                        .queryParam("serviceKey", props.getServiceKey())
                        .queryParam("cityCode", cityCode)
                        .queryParam("nodeId", nodeId)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(TagoResponse.class)
                .map(response -> findArrivalByRouteName(response, targetRouteName))
                .onErrorResume(e -> {
                    log.error("버스 실시간 정보 조회 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private BusArrivalInfo findArrivalByRouteName(TagoResponse response, String targetRouteName) {
        // null 체크 수정
        if (response == null || response.response == null || response.response.body == null ||
                response.response.body.items == null || response.response.body.items.item == null) {
            return null;
        }

        // 스트림 대상 수정: .items().item()
        return response.response.body.items.item.stream()
                .filter(i -> i.routeno != null && i.routeno.contains(targetRouteName))
                .findFirst()
                .map(i -> new BusArrivalInfo(i.arrprevstationcnt, i.arrtime, i.arrmsg1))
                .orElse(null);
    }

    // --- DTO ---
    public record BusArrivalInfo(Integer stopsLeft, Integer secondsLeft, String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagoResponse(BodyWrapper response) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BodyWrapper(Body body) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(ItemsWrapper items) {} // ✅ 수정
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemsWrapper(List<Item> item) {} // ✅ 수정
    record Item(
            String routeno,           // 노선 번호 (예: "매월26")
            Integer arrprevstationcnt,// 남은 정거장 수
            Integer arrtime,          // 도착 예정 시간(초)
            String arrmsg1            // 도착 메시지 (예: "3분40초후", "전전")
    ) {}
}