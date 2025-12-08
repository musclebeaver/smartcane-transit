package com.smartcane.transit.service;

import com.smartcane.transit.config.GuidanceProperties;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시각장애인용 TTS 문구 생성기
 * - WALK: step description은 스텝이 "바뀌는 순간" 단 한 번만 읽어줌
 *         이후에는 "다음 안내까지 ~미터" 위주로 안내
 * - BUS/SUBWAY: 정거장/역 개수 기반
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuidanceTextGenerator {

    private final GuidanceProperties props;

    // m를 반올림해서 정수 미터로
    private static long roundMeters(double m) {
        if (Double.isNaN(m) || Double.isInfinite(m)) return 0L;
        return Math.round(m);
    }

    // "NEXT_STEP:123.45" 형태를 파싱해서 더블(m)로 반환
    private Double parseNextStepMeters(String nextInstruction) {
        if (nextInstruction == null) return null;
        if (!nextInstruction.startsWith("NEXT_STEP:")) return null;
        try {
            String num = nextInstruction.substring("NEXT_STEP:".length()).trim();
            return Double.parseDouble(num);
        } catch (NumberFormatException e) {
            log.warn("[Guidance] NEXT_STEP 파싱 실패 nextInstruction={}", nextInstruction);
            return null;
        }
    }

    public String from(ArrivalCheckResponse arrival,
                       TripState state,
                       SkTransitRootDto.ItineraryDto itinerary,
                       SkTransitRootDto.LegDto currentLeg) {

        // -------------------------------
        // 0) 공통 파트: 모드 / phase / 남은 거리
        // -------------------------------
        double remRaw = arrival.remainingMeters();
        double remain = (Double.isNaN(remRaw) || Double.isInfinite(remRaw) || remRaw < 0) ? 9999.0 : remRaw;
        long remM = roundMeters(remain);

        String rawMode = (currentLeg.mode() != null) ? currentLeg.mode() : "WALK";
        String mode = rawMode.toUpperCase(); // "WALK" / "BUS" / "SUBWAY" ...
        String phase = (state.getPhase() != null) ? state.getPhase() : "";

        Integer stopsLeft = arrival.stopsLeft(); // BUS/SUBWAY용

        // 현재 leg가 마지막 leg인지 여부 (최종 목적지 판단용)
        boolean isLastLeg = false;
        if (itinerary != null && itinerary.legs() != null && !itinerary.legs().isEmpty()) {
            int lastIdx = itinerary.legs().size() - 1;
            isLastLeg = state.getLegIndex() >= lastIdx;
        }

        // 출발/도착 지점 이름 (대중교통 안내 문구에 사용 가능)
        String startName = (currentLeg.start() != null) ? currentLeg.start().name() : null;
        String endName   = (currentLeg.end()   != null) ? currentLeg.end().name()   : null;

        // -------------------------------
        // 0-1) WALK + 경로 이탈(offRoute) 우선 처리
        // -------------------------------
        boolean offRoute = arrival.offRoute();
        if ("WALK".equals(mode) && offRoute) {
            return "경로를 벗어났습니다. 조금 전 안내된 보행 경로 쪽으로 방향을 다시 잡아 주세요.";
        }

        // -------------------------------
        // 1) "도착"으로 판정된 경우
        // -------------------------------
        if (arrival.arrived()) {
            if ("WALK".equals(mode) && isLastLeg) {
                return "최종 목적지에 도착했습니다. 주변을 천천히 확인해 주세요.";
            }
            if ("WALK".equals(mode)) {
                return "도착 지점에 도달했습니다. 주변을 확인하시고 다음 대중교통 승강장을 찾아 이동해 주세요.";
            }

            if ("BUS".equals(mode) || "SUBWAY".equals(mode)) {
                return switch (phase) {
                    case "ONBOARD" ->
                            "하차 지점에 도착했습니다. 천천히 내리신 후, 승강장을 벗어나 안전한 곳으로 이동해 주세요.";
                    case "TRANSFER" ->
                            "환승 지점에 도착했습니다. 안내 표지판을 따라 다음 노선 승강장으로 이동해 주세요.";
                    case "ARRIVED" ->
                            "최종 목적지에 도착했습니다. 주변을 천천히 확인해 주세요.";
                    default ->
                            "도착 지점에 도달했습니다. 다음 안내에 따라 이동해 주세요.";
                };
            }
            return "도착 지점에 도달했습니다. 다음 안내에 따라 이동해 주세요.";
        }

        // ===============================
        // 2) WALK 구간
        // ===============================
        if ("WALK".equals(mode)) {

            // WalkArrivalService 에서 내려준 값들
            String stepDesc = arrival.currentInstruction();      // 현재 step 설명
            Double nextStepDistRaw = parseNextStepMeters(arrival.nextInstruction()); // 다음 안내 지점까지 거리(m)

            Long nextStepM = null;
            if (nextStepDistRaw != null && !Double.isNaN(nextStepDistRaw) && !Double.isInfinite(nextStepDistRaw)) {
                nextStepM = Math.max(0L, roundMeters(nextStepDistRaw));
            }

            Integer stepIdx = state.getStepIndex();
            Integer lastSpokenStepIdx = state.getLastSpokenStepIndex();

            boolean isNewStep = (stepIdx != null && !stepIdx.equals(lastSpokenStepIdx));

            // ★★ 핵심 규칙 ★★
            // 1) 새로운 step 으로 진입했을 때 → description 을 "딱 한 번" 읽어준다.
            // 2) 그 이후에는 계속 "다음 안내까지 ~미터 남았습니다" 위주로 안내.
            if (isNewStep && stepDesc != null && !stepDesc.isBlank()) {
                state.setLastSpokenStepIndex(stepIdx);

                if (nextStepM != null) {
                    return String.format(
                            "%s. 다음 안내까지 약 %d미터 남았습니다.",
                            stepDesc,
                            nextStepM
                    );
                } else {
                    return String.format(
                            "%s. 안내된 경로를 따라 계속 이동해 주세요.",
                            stepDesc
                    );
                }
            }

            // 2-2) 같은 step 안에서는 "다음 안내까지 ~m"만 반복
            if (nextStepM != null) {
                if (nextStepM <= 0) {
                    return "잠시 후 다음 안내가 있습니다. 속도를 줄이고 주변을 잘 살펴 주세요.";
                }
                return String.format(
                        "다음 안내까지 약 %d미터 남았습니다. 안내된 경로를 따라 계속 이동해 주세요.",
                        nextStepM
                );
            }

            // nextStepM 이 null 이면 어쩔 수 없이 남은 거리(remain)를 대체 사용
            if (remM <= 30) {
                return "잠시 후 다음 안내가 있습니다. 속도를 줄이고 주변을 잘 살펴 주세요.";
            }
            return "안내된 경로를 따라 계속 이동해 주세요.";
        }

        // ===============================
        // 3) BUS 구간
        // ===============================
        if ("BUS".equals(mode)) {
            String segment = (startName != null && endName != null)
                    ? String.format("%s에서 %s 방향 버스 구간입니다. ", startName, endName)
                    : "버스 구간입니다. ";

            if (stopsLeft != null) {
                if (stopsLeft <= 0) {
                    return segment + "곧 하차 정류장입니다. 주변 안내 방송을 확인하시고 내릴 준비를 해 주세요.";
                } else if (stopsLeft == 1) {
                    return segment + "다음 정류장에서 하차입니다. 벨을 누르고 천천히 준비해 주세요.";
                } else if (stopsLeft <= 3) {
                    return String.format(
                            "%s하차까지 %d정거장 남았습니다. 안전 손잡이를 잡고, 정차 후에만 이동해 주세요.",
                            segment, stopsLeft
                    );
                } else {
                    return String.format(
                            "%s하차까지 약 %d정거장 남았습니다. 이동 중에는 자리에 앉거나 손잡이를 잡고 계세요.",
                            segment, stopsLeft
                    );
                }
            }

            return switch (phase) {
                case "ONBOARD" ->
                        segment + "버스에 탑승 중입니다. 하차 정류장 근처에서 다시 안내해 드리겠습니다.";
                case "TRANSFER" ->
                        "환승 버스를 기다리는 구간입니다. 정류장 근처에서 버스를 기다려 주세요.";
                default ->
                        segment + "정류장에서 버스를 기다려 주세요. 도착 후 다시 안내해 드리겠습니다.";
            };
        }

        // ===============================
        // 4) SUBWAY 구간
        // ===============================
        if ("SUBWAY".equals(mode)) {
            String segment = (startName != null && endName != null)
                    ? String.format("%s에서 %s 방향 지하철 구간입니다. ", startName, endName)
                    : "지하철 구간입니다. ";

            if (stopsLeft != null) {
                if (stopsLeft <= 0) {
                    return segment + "곧 하차역입니다. 문이 열리면 주변 승객과 발판 높이를 조심해 주세요.";
                } else if (stopsLeft == 1) {
                    return segment + "다음 역에서 하차입니다. 출입문 쪽으로 천천히 이동해 주세요.";
                } else if (stopsLeft <= 3) {
                    return String.format(
                            "%s하차까지 %d개 역이 남았습니다. 좌석이나 손잡이를 잡고 안전에 유의해 주세요.",
                            segment, stopsLeft
                    );
                } else {
                    return String.format(
                            "%s하차까지 약 %d개 역이 남았습니다. 열차 이동 중에는 자리에서 이동하지 않는 것이 안전합니다.",
                            segment, stopsLeft
                    );
                }
            }

            return switch (phase) {
                case "ONBOARD" ->
                        segment + "지하철에 탑승 중입니다. 하차역 근처에서 다시 안내해 드리겠습니다.";
                case "TRANSFER" ->
                        "환승역 구간입니다. 승강장과 환승 안내 표지판을 따라 이동해 주세요.";
                default ->
                        segment + "승강장으로 이동하여 열차를 기다려 주세요.";
            };
        }

        // 5) 그 외 모드(예비 확장)
        return "경로를 따라 이동해 주세요.";
    }
}
