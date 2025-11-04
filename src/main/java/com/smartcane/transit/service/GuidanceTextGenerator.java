package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.Mode;
import com.smartcane.transit.dto.response.Itinerary;
import com.smartcane.transit.dto.response.Leg;
import org.springframework.stereotype.Component;

/**
 * 시각장애인용 TTS 문구 최소 규칙(간단 버전)
 * - 보행: 남은거리 위주
 * - 버스/지하철: 다음 정류장/역 위주 (상세는 추후 강화)
 */
@Component
public class GuidanceTextGenerator {

    public String from(ArrivalCheckResponse arrival,
                       TripState state,
                       Itinerary itinerary,
                       Leg currentLeg) {

        double remain = Math.max(0, arrival.remainingMeters());
        String mode = currentLeg.mode() != null ? currentLeg.mode().name() : "WALK";

        if (arrival.arrived()) {
            return "도착했습니다. 다음 구간으로 이동하세요.";
        }

        if (Mode.WALK.name().equalsIgnoreCase(mode)) {
            if (remain <= 15) return "곧 목적 지점입니다. 천천히 이동하세요.";
            if (remain <= 50) return String.format("앞으로 약 %.0f미터, 직진하세요.", remain);
            return String.format("다음 안내까지 %.0f미터 이동하세요.", remain);
        }

        if (Mode.BUS.name().equalsIgnoreCase(mode)) {
            return "대중교통 구간입니다. 다음 정류장 안내까지 잠시만 기다려 주세요.";
        }

        if (Mode.SUBWAY.name().equalsIgnoreCase(mode)) {
            return "지하철 구간입니다. 하차역 근접 시 안내하겠습니다.";
        }

        return "경로를 따라 이동하세요.";
    }
}
