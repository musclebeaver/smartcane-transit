package com.smartcane.transit.service;

import com.smartcane.transit.dto.response.SkTransitRootDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("redis") // prod에서만 활성화, local은 in-memory 사용
@RequiredArgsConstructor
public class RedisTripStore implements TripStore {

    private static final String KEY_STATE_PREFIX = "trip:state:";  // 상태
    private static final String KEY_META_PREFIX  = "trip:meta:";   // 메타
    private static final Duration TTL = Duration.ofHours(5);       // 3시간 유지

    // 👇 제네릭은 예시야. 지금 환경에 맞춰 타입 맞춰주면 됨.
    private final RedisTemplate<String, TripState> stateRedisTemplate;
    private final RedisTemplate<String, SkTransitRootDto.MetaDataDto> metaRedisTemplate;

    private String stateKey(String tripId) {
        return KEY_STATE_PREFIX + tripId;
    }

    private String metaKey(String tripId) {
        return KEY_META_PREFIX + tripId;
    }

    @Override
    public void init(String tripId,
                     SkTransitRootDto.MetaDataDto meta,
                     int itineraryIndex,
                     int legIndex,
                     Integer stepIndex,
                     String phase) {

        TripState state = new TripState(tripId, itineraryIndex, legIndex, stepIndex, phase);
        stateRedisTemplate.opsForValue().set(stateKey(tripId), state, TTL);

        if (meta != null) {
            metaRedisTemplate.opsForValue().set(metaKey(tripId), meta, TTL);
        }
    }

    @Override
    public TripState load(String tripId) {
        return stateRedisTemplate.opsForValue().get(stateKey(tripId));
    }

    @Override
    public void save(String tripId, TripState state) {
        if (state != null) {
            stateRedisTemplate.opsForValue().set(stateKey(tripId), state, TTL);
        }
    }

    @Override
    public SkTransitRootDto.MetaDataDto loadMeta(String tripId) {
        return metaRedisTemplate.opsForValue().get(metaKey(tripId));
    }
}
