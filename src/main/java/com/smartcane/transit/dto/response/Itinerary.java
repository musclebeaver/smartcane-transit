// dto/response/Itinerary.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Itinerary(
        Fare fare,
        Integer totalTime,
        List<Leg> legs,
        Integer totalWalkTime,
        Integer transferCount,
        Integer totalDistance,
        Integer pathType,
        Integer totalWalkDistance
) {}
