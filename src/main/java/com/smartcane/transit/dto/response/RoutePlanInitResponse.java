package com.smartcane.transit.dto.response;

/**
 * /api/transit/plan 응답: 서버가 tripId를 발급하고
 * MetaData(경로 결과)와 함께 내려준다.
 */
public record RoutePlanInitResponse(
        String tripId,
        MetaData metaData
) {}
