// dto/response/Station.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Station(
        Integer index,
        String stationName,
        String lon,   // ⚠️ 문자열로 내려옴
        String lat,   // ⚠️ 문자열로 내려옴
        String stationID
) {}
