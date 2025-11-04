package com.smartcane.transit.service;

public class TripState {
    private String tripId;
    private int itineraryIndex;
    private int legIndex;
    private Integer stepIndex;        // WALK일 때 사용 (null 가능)
    private String phase;             // WALKING/ONBOARD/TRANSFER/ARRIVED...
    private double lastLon;
    private double lastLat;
    private long lastTs;
    private double cumulativeWalkMeter;

    public TripState() {}

    public TripState(String tripId, int itineraryIndex, int legIndex, Integer stepIndex, String phase) {
        this.tripId = tripId;
        this.itineraryIndex = itineraryIndex;
        this.legIndex = legIndex;
        this.stepIndex = stepIndex;
        this.phase = phase;
    }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public int getItineraryIndex() { return itineraryIndex; }
    public void setItineraryIndex(int itineraryIndex) { this.itineraryIndex = itineraryIndex; }
    public int getLegIndex() { return legIndex; }
    public void setLegIndex(int legIndex) { this.legIndex = legIndex; }
    public Integer getStepIndex() { return stepIndex; }
    public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public double getLastLon() { return lastLon; }
    public void setLastLon(double lastLon) { this.lastLon = lastLon; }
    public double getLastLat() { return lastLat; }
    public void setLastLat(double lastLat) { this.lastLat = lastLat; }
    public long getLastTs() { return lastTs; }
    public void setLastTs(long lastTs) { this.lastTs = lastTs; }
    public double getCumulativeWalkMeter() { return cumulativeWalkMeter; }
    public void setCumulativeWalkMeter(double cumulativeWalkMeter) { this.cumulativeWalkMeter = cumulativeWalkMeter; }

    private int arrivalStreak = 0; // 도착 조건 연속 만족 횟수(히스테리시스)
    private final java.util.ArrayDeque<Double> latBuf = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Double> lonBuf = new java.util.ArrayDeque<>();

    public int getArrivalStreak() { return arrivalStreak; }
    public void setArrivalStreak(int s) { this.arrivalStreak = s; }
    public java.util.ArrayDeque<Double> getLatBuf() { return latBuf; }
    public java.util.ArrayDeque<Double> getLonBuf() { return lonBuf; }
}
