package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * /api/transit/plan 응답 DTO
 * - tripId: 서버가 발급한 세션 ID
 * - metaData: SK 길찾기 응답의 MetaData (필터링된 itineraries 포함)
 */
public record RoutePlanInitResponse(
        @JsonProperty("tripId") String tripId,
        @JsonProperty("metaData") SkTransitRootDto.MetaDataDto metaData
) {}
