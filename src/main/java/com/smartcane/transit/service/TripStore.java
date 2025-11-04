package com.smartcane.transit.service;

public interface TripStore {
    void init(String tripId, int itineraryIndex, int legIndex, Integer stepIndex, String phase);
    TripState load(String tripId); // 없으면 null
    void save(String tripId, TripState state);
}
