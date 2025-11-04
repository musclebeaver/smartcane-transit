// dto/response/Mode.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum Mode {
    WALK, SUBWAY, BUS, TRAIN, AIRPLANE, FERRY, WIDEAREA, SUBWAYBUS,
    @JsonEnumDefaultValue UNKNOWN
}
