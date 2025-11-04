// dto/response/RequestParameters.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RequestParameters(
        Integer busCount,
        Integer expressbusCount,
        Integer subwayCount,
        Integer airplaneCount,
        String  locale,
        String  endY,
        String  endX,
        Integer wideareaRouteCount,
        Integer subwayBusCount,
        String  startY,
        String  startX,
        Integer ferryCount,
        Integer trainCount,
        String  reqDttm
) {}
