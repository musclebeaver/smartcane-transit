package com.smartcane.transit.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTripStore implements TripStore {
    private final Map<String, TripState> store = new ConcurrentHashMap<>();

    @Override
    public void init(String tripId, int itineraryIndex, int legIndex, Integer stepIndex, String phase) {
        store.put(tripId, new TripState(tripId, itineraryIndex, legIndex, stepIndex, phase));
    }

    @Override
    public TripState load(String tripId) {
        return store.get(tripId);
    }

    @Override
    public void save(String tripId, TripState state) {
        store.put(tripId, state);
    }
}
