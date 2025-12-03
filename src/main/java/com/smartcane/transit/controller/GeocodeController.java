// src/main/java/com/smartcane/transit/geocode/GeocodeController.java
package com.smartcane.transit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcane.transit.dto.response.GeocodeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/geocode")
@CrossOrigin(origins = "http://localhost:5173") // 프론트(dev)에서 호출 허용
public class GeocodeController {

    @Value("${vworld.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<GeocodeResult> geocode(@RequestParam String address) {
        // vWorld URL 생성
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.vworld.kr/req/address")
                .queryParam("service", "address")
                .queryParam("request", "getcoord")
                .queryParam("version", "2.0")
                .queryParam("crs", "epsg:4326")
                .queryParam("address", address)
                .queryParam("refine", "true")
                .queryParam("simple", "false")
                .queryParam("format", "json")
                .queryParam("type", "road")
                .queryParam("key", apiKey)
                .build()
                .toUriString();

        String body = RestClient.create()
                .get()
                .uri(url)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root.path("response");
            String status = response.path("status").asText();

            if (!"OK".equals(status)) {
                throw new IllegalStateException("vWorld 응답 status != OK");
            }

            JsonNode point = response.path("result").path("point");
            double x = point.path("x").asDouble();
            double y = point.path("y").asDouble();

            GeocodeResult result = new GeocodeResult(x, y);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new RuntimeException("vWorld 응답 파싱 실패", e);
        }
    }
}
