// response/ArrivalCheckResponse.java
package com.smartcane.transit.dto.response;

public record ArrivalCheckResponse(
        boolean arrived,           // 목표 지점(leg/step의 끝)에 도달했는가
        double remainingMeters,    // 남은 거리 (leg or step 기준)
        String currentInstruction, // 현재 안내 문구 (WALK step이면 step.description 우선) 현재 안내(없으면 기본 “직진하세요.”)
        String nextInstruction,    // 다음 안내 미리고지 (lookAhead 안이면)
        Integer nextLegIndex,          // 다음으로 넘어가야 할 leg index (도착 시)
        Integer nextStepIndex      // 다음으로 넘어가야 할 step index (WALK 시)
) {}
