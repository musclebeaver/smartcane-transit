// dto/response/Place.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Place(
        String name,
        Double lon,
        Double lat
) {}
