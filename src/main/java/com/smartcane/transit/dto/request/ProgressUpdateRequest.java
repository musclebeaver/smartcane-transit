package com.smartcane.transit.dto.request;

/**
 * iOS → 서버 진행 업링크 표준 DTO
 * (전경/백그라운드 모두 사용 가능. Redis 도입 전에는 MetaData를 함께 보내도 됨)
 */
public record ProgressUpdateRequest(
        double lon,
        double lat,
        Double speedMps,          // 선택
        Long timestampEpochMs,    // 선택
        Double headingDeg,        // 선택
        Double arriveRadiusM,      // 선택: 기본 20~25m 추천
        Double lookAheadM         // 선택: ArrivalCheckRequest.lookAheadM 와 매핑(예: 25m)
) {}
