// request/ArrivalCheckRequest.java
package com.smartcane.transit.dto.request;

public record ArrivalCheckRequest(
        double currLat,
        double currLon,
        int itineraryIndex,    // 기본 0 사용
        int legIndex,          // 현재 진행중인 Leg index
        Integer stepIndex,     // WALK일 경우 현재 step index (없으면 null)
        double arriveRadiusM,  // 도착 판정 반경 (예: 12.0m)
        Double lookAheadM      // 다음 안내를 미리 말해줄 거리 (예: 25.0m)
) {}
