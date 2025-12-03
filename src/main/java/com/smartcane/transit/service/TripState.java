package com.smartcane.transit.service;

import java.util.ArrayDeque;

public class TripState {

    // ✅ phase 문자열 상수 모아두기
    public static final String PHASE_WALKING         = "WALKING";
    public static final String PHASE_WAITING_TRANSIT = "WAITING_TRANSIT";
    public static final String PHASE_ONBOARD         = "ONBOARD";
    public static final String PHASE_TRANSFER        = "TRANSFER";
    public static final String PHASE_ARRIVED         = "ARRIVED";
    public static final String PHASE_CANCELLED       = "CANCELLED";

    private String tripId;
    private int itineraryIndex;
    private int legIndex;
    private Integer stepIndex;        // WALK일 때 사용 (null 가능)
    private String phase;             // 위 상수들 중 하나 사용

    private double lastLon;
    private double lastLat;
    private long lastTs;
    private double cumulativeWalkMeter;

    // 도착 히스테리시스용
    private int arrivalStreak = 0;                 // 도착 조건 연속 만족 횟수
    private final ArrayDeque<Double> latBuf = new ArrayDeque<>();
    private final ArrayDeque<Double> lonBuf = new ArrayDeque<>();

    // 🔹 새로 추가
    /** 마지막으로 description 을 안내한 step 인덱스 (처음 진입 여부 판단용) */
    private Integer lastAnnouncedStepIndex;

    /** 마지막으로 "다음 안내까지 ~m"을 말했을 때의 거리 */
    private Double lastAnnouncedDistToNextStep;

    private Integer lastSpokenStepIndex;  // 마지막으로 description을 읽어준 step 인덱스

    public TripState() {
    }

    public TripState(String tripId, int itineraryIndex, int legIndex,
                     Integer stepIndex, String phase) {
        this.tripId = tripId;
        this.itineraryIndex = itineraryIndex;
        this.legIndex = legIndex;
        this.stepIndex = stepIndex;
        this.phase = phase;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public int getItineraryIndex() {
        return itineraryIndex;
    }

    public void setItineraryIndex(int itineraryIndex) {
        this.itineraryIndex = itineraryIndex;
    }

    public int getLegIndex() {
        return legIndex;
    }

    public void setLegIndex(int legIndex) {
        this.legIndex = legIndex;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public double getLastLon() {
        return lastLon;
    }

    public void setLastLon(double lastLon) {
        this.lastLon = lastLon;
    }

    public double getLastLat() {
        return lastLat;
    }

    public void setLastLat(double lastLat) {
        this.lastLat = lastLat;
    }

    public long getLastTs() {
        return lastTs;
    }

    public void setLastTs(long lastTs) {
        this.lastTs = lastTs;
    }

    public double getCumulativeWalkMeter() {
        return cumulativeWalkMeter;
    }

    public void setCumulativeWalkMeter(double cumulativeWalkMeter) {
        this.cumulativeWalkMeter = cumulativeWalkMeter;
    }

    public int getArrivalStreak() {
        return arrivalStreak;
    }

    public void setArrivalStreak(int arrivalStreak) {
        this.arrivalStreak = arrivalStreak;
    }

    public ArrayDeque<Double> getLatBuf() {
        return latBuf;
    }

    public ArrayDeque<Double> getLonBuf() {
        return lonBuf;
    }


    // getter / setter 추가
    public Integer getLastAnnouncedStepIndex() {
        return lastAnnouncedStepIndex;
    }

    public void setLastAnnouncedStepIndex(Integer lastAnnouncedStepIndex) {
        this.lastAnnouncedStepIndex = lastAnnouncedStepIndex;
    }

    public Double getLastAnnouncedDistToNextStep() {
        return lastAnnouncedDistToNextStep;
    }

    public void setLastAnnouncedDistToNextStep(Double lastAnnouncedDistToNextStep) {
        this.lastAnnouncedDistToNextStep = lastAnnouncedDistToNextStep;
    }

    public Integer getLastSpokenStepIndex() {
        return lastSpokenStepIndex;
    }

    public void setLastSpokenStepIndex(Integer lastSpokenStepIndex) {
        this.lastSpokenStepIndex = lastSpokenStepIndex;
    }
}
