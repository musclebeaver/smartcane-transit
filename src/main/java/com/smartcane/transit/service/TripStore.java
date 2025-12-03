// package 는 기존 TripStore 가 있는 곳에 맞춰 주세요.
package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.SkTransitRootDto;

public interface TripStore {

    /**
     * tripId 기준으로
     * - MetaData (경로 전체 정보)
     * - 초기 TripState (현재 itinerary/leg/step, phase)
     * 를 함께 초기화.
     */
    void init(String tripId,
              SkTransitRootDto.MetaDataDto meta,
              int itineraryIndex,
              int legIndex,
              Integer stepIndex,
              String phase);

    /**
     * 현재 상태 조회
     */
    TripState load(String tripId);

    /**
     * 상태 저장
     */
    void save(String tripId, TripState state);

    /**
     * 경로 메타데이터 조회
     */
    SkTransitRootDto.MetaDataDto loadMeta(String tripId);
}
