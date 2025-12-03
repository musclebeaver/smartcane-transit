package com.smartcane.transit.dto.response;

public record GeocodeResult(
        double x,  // 경도
        double y   // 위도
) {}