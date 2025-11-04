// dto/response/Step.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Step(
        String streetName,
        Integer distance,
        String description,
        String linestring
) {}
