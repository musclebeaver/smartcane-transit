package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.SkTransitRootDto.ItineraryDto;

import java.util.Comparator;
import java.util.List;

public class SkRouteSelector {

    /**
     * SK API 전체 결과 중 우선순위 정책에 따라 **단 1개의 최적 경로**만 반환합니다.
     * 1순위: 버스 위주 (pathType == 2) 중 최단 시간
     * 2순위: 지하철+버스 (pathType == 3) 중 최단 시간
     * 3순위: 그 외 전체 중 최단 시간
     */
    public List<ItineraryDto> selectPreferredItineraries(List<ItineraryDto> all) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        // 1) 버스 위주 (pathType=2) 탐색
        List<ItineraryDto> busOnly = all.stream()
                .filter(it -> it.pathType() == 2)
                .sorted(Comparator.comparingInt(ItineraryDto::totalTime)) // 시간순 정렬
                .toList();

        if (!busOnly.isEmpty()) {
            // [수정] 가장 빠른 1개만 선택해서 반환
            return List.of(busOnly.get(0));
        }

        // 2) 지하철+버스 (pathType=3) 탐색
        List<ItineraryDto> subwayBus = all.stream()
                .filter(it -> it.pathType() == 3)
                .sorted(Comparator.comparingInt(ItineraryDto::totalTime))
                .toList();

        if (!subwayBus.isEmpty()) {
            // [수정] 가장 빠른 1개만 선택해서 반환
            return List.of(subwayBus.get(0));
        }

        // 3) 둘 다 없으면 전체 중에서 가장 빠른 것 1개
        return all.stream()
                .sorted(Comparator.comparingInt(ItineraryDto::totalTime))
                .limit(1) // [수정] 스트림에서 1개만 남김
                .toList();
    }
}