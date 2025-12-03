package com.smartcane.transit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 진행/도착 판정 및 필터링 파라미터 (운영 중에도 yml로 조정)
 */
@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "smartcane.transit")
public class GuidanceProperties {

    // --- 거리 임계 ---
    private double arriveRadiusWalkM   = 14.0;  // 보행 도착 반경(권장 12~15)
    private double arriveRadiusTransitM= 22.0;  // 대중교통 도착 반경(권장 20~25)
    private double lookAheadWalkM      = 25.0;  // 보행 프리뷰 거리(권장 20~30)
    private double geofenceOffRouteM   = 28.0;  // 이탈 감지(권장 25~30)

    // --- 노이즈 억제 ---
    private int    medianWindow        = 5;     // 중앙값 필터 창 크기(3~5)
    private double minSpeedMps         = 0.3;   // 속도 게이팅 임계(정지시 튐 억제)

    // --- 히스테리시스(깜빡임 방지) ---
    private int    arrivalHysteresisN  = 1;     // 연속 N회 조건 만족 시 도착 인정 (2~3)

    // description 안내를 언제부터 쓸지 (m)
    private double descriptionTriggerM = 40.0;

    // (선택) 업링크 권고: 클라에서 1초/3m 이상 변화 시 업링크
}
