package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.SkTransitRootDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 임시 구현: 서버 메모리에 trip 상태 + 메타데이터 저장
 * - 이후 RedisTripStore 로 교체 가능
 */
@Component
public class InMemoryTripStore implements TripStore {

    private final Map<String, TripState> states = new ConcurrentHashMap<>();
    private final Map<String, SkTransitRootDto.MetaDataDto> metas = new ConcurrentHashMap<>();

    @Override
    public void init(String tripId,
                     SkTransitRootDto.MetaDataDto meta,
                     int itineraryIndex,
                     int legIndex,
                     Integer stepIndex,
                     String phase) {

        TripState state = new TripState(tripId, itineraryIndex, legIndex, stepIndex, phase);
        states.put(tripId, state);

        if (meta != null) {
            metas.put(tripId, meta);
        }
    }

    @Override
    public TripState load(String tripId) {
        return states.get(tripId);
    }

    @Override
    public void save(String tripId, TripState state) {
        if (state != null) {
            states.put(tripId, state);
        }
    }

    @Override
    public SkTransitRootDto.MetaDataDto loadMeta(String tripId) {
        return metas.get(tripId);
    }
}
