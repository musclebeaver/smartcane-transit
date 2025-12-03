package com.smartcane.transit.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartcane.transit.dto.response.SkTransitRootDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressUpdateEnvelope(

        /**
         * (선택)
         * - 보통은 null 로 두고,
         * - 서버에서 TripStore.loadMeta(tripId) 가 null 일 때
         *   클라이언트가 metaData 를 다시 보내는 용도로만 사용 가능.
         */
        @JsonProperty("metaData")
        SkTransitRootDto.MetaDataDto metaData,

        /**
         * 필수: 진행 정보
         */
        @JsonProperty("progress")
        ProgressUpdateRequest progress
) {}
