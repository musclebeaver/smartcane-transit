// dto/response/Leg.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Leg(
        Mode mode,
        Integer sectionTime,
        Integer distance,

        // 공통 위치
        Place start,
        Place end,

        // 대중교통일 때 (SUBWAY/BUS 등)
        String routeColor,
        String route,
        String routeId,
        Integer service,
        Integer type,
        PassStopList passStopList,
        PassShape passShape,

        // 도보일 때
        List<Step> steps
) {}
