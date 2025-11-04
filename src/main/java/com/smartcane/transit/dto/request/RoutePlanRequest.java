// dto/request/RouteQuery.java
package com.smartcane.transit.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutePlanRequest(
        @JsonProperty("startX") String startX,
        @JsonProperty("startY") String startY,
        @JsonProperty("endX")   String endX,
        @JsonProperty("endY")   String endY,
        @JsonProperty("lang")   Integer lang,        // 0(ko) 등
        @JsonProperty("format") String format,       // "json"
        @JsonProperty("count")  Integer count,       // 경로 개수
        @JsonProperty("searchDttm") String searchDttm // "yyyyMMddHHmm"
) {}
