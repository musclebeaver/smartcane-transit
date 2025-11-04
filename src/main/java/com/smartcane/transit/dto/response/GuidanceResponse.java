package com.smartcane.transit.dto.response;

public record GuidanceResponse(
        String tripId,
        int itineraryIndex,
        int legIndex,
        String phase,             // WALKING/ONBOARD/TRANSFER/ARRIVED...
        String tts,     // 음성 안내 문구
        double distanceToTargetM,  // 남은 거리
        Integer etaToTargetSec // 선택
) {}
