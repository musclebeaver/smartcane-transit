# 🚍 대중교통 길찾기 백엔드 (버스/지하철) — SmartCane Transit API

시각·이동 약자를 위한 **버스/지하철 경로 안내** 백엔드입니다.  
혼합 경로 탐색(버스+지하철), 실시간 도착 정보, 승·하차 추정(센서 이벤트 기반)을 제공합니다.

> ✅ **Tech Stack**
> - **Backend**: Spring Boot (Java 21), Spring MVC, Spring Data JPA, Spring Validation, (옵션) Spring Security  
> - **Build/Tools**: Gradle, Docker  
> - **Infra**: AWS ECS/RDS/CloudWatch, GitHub Actions  
> - **Transit API**: SK 대중교통 API(경로/정류장/도착)

---

## 🤖 AI 시스템 아키텍처 & 기술 설명

### 1. 개요
본 **스마트 지팡이**는 대중교통 안내와 보행 안전을 통합합니다.  
교통 안내는 서버에서, 승·하차 추정은 **모바일 센서 + 서버 로직의 하이브리드**로 처리하여 실시간성과 안정성을 확보합니다.

> ✅ **핵심 특징**
- **혼합 경로 탐색**: 버스/지하철/환승 자동 최적화
- **센서 융합**: GPS + 가속도/자이로 + Wi‑Fi/Beacon + (옵션) 기기 카메라 AI
- **저지연 안내**: 모바일 로컬 판단 + 서버 검증의 투 트랙


---

## 🧭 기능 개요
- **경로 탐색**: 버스/지하철/혼합 모드 지원(출발–도착 좌표/POI)
- **정류장·역 주변 검색**: 반경 내 근접 정류장/역 조회
- **실시간 도착 정보**: 버스/지하철 도착 예정 갱신
- **승·하차 추정**: 센서 이벤트 + 지오펜스 + 정류장/역 메타데이터 기반 상태 추정
- **여정 관리**: 진행 중 여정의 남은 정거장/환승/알림 제공

---

## 📡 데이터 소스
- **SK 대중교통 API**: 노선/경로/정류장/도착 정보
- **오픈 데이터(선택)**: 지하철 역사 좌표/출구/심도 정보
- **모바일 센서**: GPS, IMU(가속도/자이로), Wi‑Fi/Beacon 스캔, (옵션) 카메라 이벤트

---

## 🔗 API 설계 (초안)

### 경로 탐색
```
POST /api/transit/plan : 경로 생성(출발/도착 좌표 입력 → 표준화된 Itinerary 반환 + tripId)

POST /api/transit/trips/{tripId}/progress : 진행상황 업링크(iOS 위치/센서) → 현재 구간/다음 안내 반환

GET /api/transit/trips/{tripId} : 현재 Trip 상태 조회(디버깅/복구용)

POST /api/transit/trips/{tripId}/event : (옵션) 승/하차/환승 확정 이벤트 업링크

GET /api/transit/stops/nearby : 반경 내 정류장/역 검색(보조
```

### 주변 정류장/역
```

```

### 실시간 도착
```

```

### 승·하차 이벤트(센서 업링크)
```

```

### 여정 상태/알림
```

```

> 문서화: `/docs` (springdoc-openapi), 상태확인: `/actuator/health`

---

## 🗂 디렉토리 구조 (Backend만)
```
smartcane-transit/
└─ backend/
   ├─ build.gradle
   ├─ settings.gradle
   ├─ Dockerfile
   ├─ scripts/
   │   ├─ run-local.sh
   │   └─ wait-for-it.sh
   └─ src/
      ├─ main/
      │  ├─ java/com/smartcane/transit/
      │  │  ├─ TransitApplication.java
      │  │  ├─ config/
      │  │  │  ├─ WebConfig.java
      │  │  │  ├─ OpenApiConfig.java
      │  │  │  ├─ SKTransitProperties.java
      │  │  │  └─ SecurityConfig.java 
      │  │  ├─ controller/
      │  │  │  ├─ RouteController.java
      │  │  │  ├─ StopController.java
      │  │  │  └─ TransitController.java
      │  │  ├─ service/
      │  │  │  ├─ RouteService.java
      │  │  │  ├─ StopService.java
      │  │  │  └─ TripService.java
      │  │  ├─ entity/
      │  │  ├─ repository/
      │  │  ├─ dto/
      │  │  │  ├─ request/
      │  │  │  └─ response/
      │  │  ├─ exception/
      │  └─ resources/
      │     ├─ application.yml
      │     ├─ application-dev.yml
      │     └─ application-prod.yml
      └─ test/java/com/smartcane/transit/
         └─ ... (단위/통합 테스트)
```

---

## ⚙️ 실행 방법

### 1) 로컬
```bash
# 1) 환경 변수 설정
export SK_API_KEY=xxxxx
export SPRING_PROFILES_ACTIVE=dev

# 2) 빌드 & 실행
./gradlew clean bootRun
# or
./gradlew build && java -jar build/libs/transit-*.jar
```

### 2) Docker
```bash
docker build -t smartcane/transit:dev .
docker run -p 8080:8080 -e SK_API_KEY=xxxxx smartcane/transit:dev
```

---

## 🔐 설정(예시)
```yaml
# application.yml
server:
  port: 8084

spring:
  application:
    name: smartcane-transit
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

logging:
  level:
    root: INFO
    org.springframework.web: INFO
    com.smartcane.transit: DEBUG
sk:
  transit:
    base-url: https://apis.openapi.sk.com/transit/routes/ # 실제 엔드포인트 넣기
    api-key: ${TRANSIT_API_KEY:change-me}    # 환경변수/시크릿로 대체 권장
```

---

## 🧠 승·하차 추정 로직(개요)
- **Boarding 후보**: 정류장/역 지오펜스 내 + (버스) 차량 이동 시작 전 가속 패턴 + Wi‑Fi SSID/BSSID 근접  
- **Onboard 유지**: 지속적 선형 이동 + GNSS 속도 일관성 + 지하(역) 구간에서 GPS drop 시 IMU dead‑reckoning 보정  
- **Alighted 후보**: 속도 → 0 + 도어 오픈 패턴(진동/소리, 선택) + 지오펜스 이탈
- **검증**: 실시간 도착/통과 이벤트 대조(노선/역 메타데이터)

---

## 🧩 해결해야 할 과제(이슈 트래킹)
1. **SK API 보완**: 최적경로로 추적  
2. **지하철 안내 시 GPS 수신 한계**: 지하·터널 구간에서 IMU + Wi‑Fi/Beacon + 지오펜스 기반 보정(Dead‑Reckoning)  
3. **승차 여부 기술 판별**: 센서 융합 + 정류장/역 이벤트 매칭으로 ‘탑승/하차/환승’ 상태 신뢰도 상향  
4. **ETA 신뢰도**: 혼잡/돌발 상황 반영(실시간 데이터 가중/보정)  
5. **배터리/데이터 최적화**: 센서 샘플링 주기·업링크 주기 동적 조절  
6. **개인화 접근성**: 진동/음성/점자 디스플레이(옵션) 알림 채널 튜닝

> GitHub Issues에 동일 타이틀로 등록 권장: `feat(route-mixed-subway)`, `fix(gps-subway-drift)`, `feat(trip-boarding-detection)` ...

---

## 🛣 로드맵
- [ ] **MVP**: 도착 경로 탐색 + 주변 정류장 또는 지하철 + 실시간 도착 + 기초 승·하차 추정  
- [ ] **혼합 경로**: 지하철 포함 경로 탐색/환승 권고  
- [ ] **지하 보정**: GPS Drop 대응 센서 융합 고도화  
- [ ] **iOS 앱 연동**: 센서 업링크/알림 UX, 접근성 가이드라인(VoiceOver)  
- [ ] **운영화**: 에러 추적/메트릭/알림, Canary, 회귀 테스트 CI

---

## 🧪 테스트 전략
- **단위 테스트**: 서비스/유틸 (지오펜스, 센서 필터)
- **통합 테스트**: SK API Stub + Controller 슬라이스
- **리플레이 테스트**: 실제 이동 로그(익명화)로 승·하차 상태 재현

---

## 🛡 보안/프라이버시
- API Key/Secret은 환경 변수 또는 시크릿 매니저 사용
- 위치·센서 데이터는 최소 수집·목적 제한·보존기간 정책 준수
- 전송 구간 TLS, 저장 시 민감 데이터 비식별화/암호화

---

## 📄 라이선스
MIT (또는 조직 정책에 맞게 업데이트)

---

## 🤝 기여 방법
1. 이슈 등록 → 라벨(`feat`, `bug`, `design`)  
2. 브랜치 전략: `main`(배포), `develop`(통합), `feature/*`  
3. PR 템플릿 준수, 단위/통합 테스트 통과 필수
