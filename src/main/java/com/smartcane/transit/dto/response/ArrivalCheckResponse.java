// response/ArrivalCheckResponse.java
package com.smartcane.transit.dto.response;

/**
 * 도착/진행 판정 공통 응답 DTO
 *
 * - arrived           : (leg 또는 전체) 도착 여부
 * - remainingMeters   : 남은 거리 (보행: 현재 leg 끝까지, 대중교통: 남은 전체 거리/구간)
 * - currentInstruction: 지금 즉시 읽어줄 안내 문구
 * - nextInstruction   : 다음 안내를 위한 힌트 (예: "NEXT_STEP:123.4" 형식)
 * - nextLegIndex      : 다음으로 넘어가야 할 leg index (leg 도착 시)
 * - nextStepIndex     : (히스테리시스 이후) 다음 step/정류장 인덱스
 * - currentStepIndex  : WALK 기준, 현재 위치에서 가장 가까운 step 인덱스
 * - currentStationIndex: BUS/SUBWAY 기준, 현재 위치에서 가장 가까운 정류장 인덱스
 * - stopsLeft         : 남은 정류장 수
 * - offRoute          : 경로 이탈 여부
 */
public record ArrivalCheckResponse(
        boolean arrived,
        double remainingMeters,
        String currentInstruction,
        String nextInstruction,
        Integer nextLegIndex,
        Integer nextStepIndex,      // 히스테리시스 이후 전이용 (주로 transit)
        Integer currentStepIndex,   // 👈 WALK: 현재 스냅된 step 인덱스
        Integer currentStationIndex,
        Integer stopsLeft,
        boolean offRoute
) {}
