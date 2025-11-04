package com.smartcane.transit.dto.request;

public record TripEventRequest(
        String type,      // "BOARD","ALIGHT","TRANSFER_CONFIRMED","ARRIVED","CANCEL" 등
        String payload    // 선택: 부가정보(역명/정류장ID 등)
) {}