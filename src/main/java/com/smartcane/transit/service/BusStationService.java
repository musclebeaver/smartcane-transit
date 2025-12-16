package com.smartcane.transit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartcane.transit.config.PublicDataProperties;
import com.smartcane.transit.util.GeoUtils;
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
public class BusStationService {

    private final PublicDataProperties props;

    // 공공데이터포털 API용 WebClient 생성 (ServiceKey 인코딩 방지)
    private WebClient getClient() {
        // ✅ 하드코딩 제거 -> props.getStationBaseUrl() 사용
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(props.getStationBaseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        return WebClient.builder()
                .uriBuilderFactory(factory)
                .baseUrl(props.getStationBaseUrl())
                .build();
    }
    /**
     * 좌표(위도, 경도)로 가장 가까운 공공데이터 정류소 정보 조회
     */
    public Mono<PublicStationInfo> findNearestStation(double lat, double lon) {
        return getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getCrdntPrxmtSttnList") // 좌표기반근접정류소목록조회
                        .queryParam("serviceKey", props.getServiceKey())
                        .queryParam("gpsLati", lat)
                        .queryParam("gpsLong", lon)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(StationResponse.class)
                .map(res -> pickBestMatch(res, lat, lon))
                .onErrorResume(e -> {
                    log.error("공공데이터 정류소 조회 실패: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    // 결과 목록 중 가장 가까운 것 하나 선택
    private PublicStationInfo pickBestMatch(StationResponse res, double lat, double lon) {
        // null 체크 단계가 늘어남
        if (res == null || res.response == null || res.response.body == null ||
                res.response.body.items == null || res.response.body.items.item == null) {
            return null;
        }

        List<StationItem> itemList = res.response.body.items.item; // ✅ .item으로 접근
        if (itemList.isEmpty()) return null;

        // 가장 가까운 정류소 1개 리턴
        StationItem best = itemList.get(0);

        return new PublicStationInfo(best.nodeid, best.citycode, best.nodenm);
    }

    // --- 내부 DTO ---
    public record PublicStationInfo(String nodeId, String cityCode, String nodeName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationResponse(BodyWrapper response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BodyWrapper(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(ItemsWrapper items) {} // ✅ items는 객체로 감싸져 있음

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemsWrapper(List<StationItem> item) {} // ✅ 그 안에 'item'이라는 리스트가 있음

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationItem(String nodeid, String citycode, String nodenm, Double gpslati, Double gpslong) {}
}