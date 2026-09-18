# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

로또 6/45 당첨 이력을 수집·조회하고, Smile ML(RandomForest)로 다음 회차 번호를 예측·검증하는 웹 애플리케이션.

- Spring Boot 4.0.1, Java 17, Gradle 9.2.1
- Spring Data JPA + MariaDB, Thymeleaf(SSR), Lombok
- Apache POI 5.2.5 (엑셀 업로드), Smile 3.1.1 (머신러닝)
- Jackson 3 사용 (`tools.jackson.databind` 패키지)

## Build & Development Commands

```bash
# 개발 서버 실행 (기본 포트 8080)
./gradlew bootRun

# 빌드
./gradlew build

# 정리
./gradlew clean
```

- Gradle은 JDK 17 이상으로 실행해야 한다. 셸 기본 Java가 8이면 `JAVA_HOME`을 JDK 17로 지정한다.
- `-Pprofile=<name>`으로 `src/main/resources/<name>` 리소스를 추가한다 (기본 `local`).
- 테스트 코드는 현재 없다.

## Configuration

`application.yml` 주요 환경 변수:

| 변수 | 기본값 | 용도 |
|------|--------|------|
| `MARIADB_URL` | `localhost:3306` | DB 호스트 (DB 이름 `lotto`) |
| `MARIADB_USERNAME` / `MARIADB_PASSWORD` | `root` / 빈 값 | DB 계정 |
| `LOTTO_PATTERN_MODEL_FILE` | `models/lotto-pattern.bin` | A 점수 모델 파일 위치 |
| `HOME_DIR` | - | 로그 파일 위치 (`${HOME_DIR}/logs/lotto.log`) |

- `lotto.model.check-interval-ms` (기본 3600000): 두 예측 모델의 이력 변경 점검 주기.
- `ddl-auto: none`: 테이블(`lotto_history`)은 직접 관리한다.
- `open-in-view: false`, Thymeleaf 캐시 비활성화.
- `models/`, `logs/`는 git에서 제외된다.

## Architecture

Base package: `com.manage.lotto`

| 패키지 | 역할 |
|--------|------|
| `controller/` | MVC 컨트롤러 (`@Controller`, Thymeleaf 뷰 이름 반환) |
| `controller/api/` | REST 컨트롤러 (`@RestController`, JSON 반환) + `ApiExceptionHandler` |
| `service/` | 비즈니스 로직 |
| `domain/` | `LottoHistory` 엔티티, `LottoRules`(번호 범위·연속 회차 검증) |
| `dto/` | 요청/응답 record |
| `event/` | `LottoHistoryChanged` (이력 변경 이벤트) |
| `exception/` | 도메인 예외 |
| `importer/` | 외부 데이터 적재 (`DonghaengApiClient`, `LottoExcelParser`) |
| `ml/` | 특징 추출, 모델 학습·저장·추론 |
| `repository/` | Spring Data JPA 리포지토리 |
| `config/` | `SchedulingConfig` (`@EnableScheduling`) |

### 화면 (HomeController)

| 경로 | 템플릿 | 내용 |
|------|--------|------|
| `GET /` | `index` | 당첨 이력 조회 |
| `GET /lotto/prediction` (`/prediction` 리다이렉트) | `prediction` | 점수 모델(A)·확률 모델(B)·인기 조합 제외(C)로 다음 회차 1게임씩, 모델 상태 |
| `GET /lotto/register` | `register` | 엑셀 대량등록 / 회차 1건 등록 |
| `GET /validation` | `validation` | 시간순 검증 (무작위와 통계 비교) |

### REST API

| API | 설명 |
|-----|------|
| `GET /api/lotto/draws` | 등록된 회차 번호 목록 |
| `GET /api/lotto/history?fromDrawNo=&toDrawNo=` | 회차 범위 당첨 이력 |
| `GET /api/lotto/recommend` | v1 확률 모델 5게임 추천 (UI 호출처 없음, 유지 대상) |
| `POST /api/lotto/history` | 회차 1건 등록 |
| `GET /api/lotto/prediction` | A·B·C 1게임씩 (A·B 중 실패한 모델은 그 게임만 `available=false`) |
| `GET /api/lotto/prediction/status` | 학습 진행 여부와 모델별 상태 |
| `POST /api/lotto/prediction/train` | 두 모델 변경 확인·재학습 요청 (202) |
| `POST /api/lotto/validation?testDraws=` | 시간순 검증 시작 (백그라운드, 202 / 실행 중이면 409) |
| `GET /api/lotto/validation` | 마지막 검증 상태·진행 단계·결과, 실행 가능한 최대 회차 수(`maxTestDraws` = 이력 수 − 50, 최대 500) |
| `POST /system/sync` | 동행복권 API 동기화 |
| `POST /system/manual/excel` | 엑셀 업로드 (기존 회차는 번호·보너스만 갱신) |

예외 응답은 `ApiExceptionHandler`가 `ErrorResponse(message)`로 변환한다: `InvalidLottoDataException`·잘못된 요청 본문 → 400, `DuplicateDrawException`·`ValidationInProgressException`·무결성 위반 → 409, `ModelNotReadyException` → 503.

### 예측 모델

번호 예측 화면(`LottoPredictionService`)은 A·B 모델과 C 규칙으로 1게임씩 만든다. A·B는 Smile RandomForest로 1~45번 공마다 다음 회차 출현 여부를 학습한다.

- **점수 모델 (A)** (`LottoPatternModelService`, `LottoPatternTrainer`, `PatternModelStore`):
  - 기본 6개 특징, RandomForest 60 trees·maxDepth 12·maxNodes 100·nodeSize 5·seed 42. 점수 상위 6개를 고르므로 모델·이력이 같으면 항상 같은 번호.
  - 클래스·설정·API 키 이름의 `pattern`은 이전 "패턴 모델"에서 유래했다. 용지 패턴 특징(공 위치, 회차 모양, 당첨 번호와의 용지 위 관계)은 시간순 검증에서 적중에 도움이 되지 않고 가장자리 번호(42·43 등)로 쏠림만 만들어 제거했다. 다시 넣지 않는다.
  - 최소 50건의 연속 이력이 필요하다. 모델을 파일로 저장하고 시작 시 로드한다.
  - 모델 파일은 Java 직렬화이므로 `SavedPatternModel` 등의 패키지·구조를 바꾸면 기존 파일을 읽지 못하고 재학습한다.
- **확률 모델 (B, v1)** (`LottoRecommendationService`, `LottoMlPredictor`, `LottoGameGenerator`):
  - 기본 6개 특징으로 번호별 출현 확률을 학습해 메모리에만 보관한다 (25회 미만이면 균등 확률). Smile 기본 설정에 트리 시드 42만 고정해 같은 이력이면 같은 확률이 나온다.
  - 확률 가중 샘플링 + 밸런스 필터로 뽑으므로 호출마다 번호가 달라질 수 있다.
  - `/api/lotto/recommend`(5게임)는 UI에서 쓰지 않지만 **삭제하지 않는다** (향후 활용 예정).
- **기본 특징 6개** (`LottoFeatureExtractor`, A·B 공통): 최근 5·10·20회 출현 수, 연속 미출현 회차 수, `co_occurrence_rate`(이 공이 나온 회차마다 직전 회차 번호가 평균 몇 개 함께 나왔는지), 누적 출현율. 모델 특징·설정을 바꾸면 `LottoPatternTrainer.MODEL_VERSION`과 `LottoRecommendationService.MODEL_VERSION`도 바꾼다.
- **인기 조합 제외 (C)** (`UnpopularGameGenerator`): 학습 없이 `SecureRandom`으로 뽑고, 6개 모두 31 이하·3연번·4개 등간격·용지(7열) 한 줄/칸/대각선 4개·직전 회차와 2개 이상 겹침 중 하나라도 해당하면 다시 뽑는다 (전체 조합의 약 32% 제외). 당첨 확률이 아니라 당첨금을 나눠 가질 사람 수를 줄이려는 규칙이며 실제 데이터로 검증하지 않았다.
- **학습 시점** (`LottoModelTrainingService`): 시작 시, `LottoHistoryChanged` 이벤트(커밋 후), `lotto.model.check-interval-ms` 주기 점검 때 한 백그라운드 스레드에서 두 모델을 차례로 확인한다.
  - 모델마다 회차·당첨 번호 지문(`HistoryFingerprint`)이 마지막 학습 때와 다를 때만 전체 이력으로 다시 학습한다.
  - 예측·추천 요청은 학습하지 않는다. 학습 실패 시 오류만 기록하고 기존 학습 결과를 유지한다.
  - 이력을 저장하는 코드는 `LottoHistoryChanged` 이벤트를 발행해야 한다.
- **시간순 검증** (`LottoValidationService`, `RandomMatchStatistics`): 최근 20~500회차를 각각 그 이전 이력만으로 맞혀 A·B·C를 비교한다. 406회차(836~1241회)로 356회차를 검증했을 때 A·B·C 모두 무작위와 차이가 없었다.
  - 특징은 한 번만 계산해 공유하고, 20회차(`REFIT_INTERVAL`)마다 다시 학습한다. 백그라운드 스레드 하나에서 한 번에 한 건만 실행하며 상태는 메모리에 마지막 1건만 둔다.
  - 무작위 기준은 시뮬레이션이 아닌 초기하분포 이론값(평균 0.8개, 3개 이상 약 2.38%)이다. 평균 일치 개수 합계의 정확한 분포로 단측 p값을 구하고, 0.05 ÷ 게임 수보다 작을 때만 "무작위보다 나음"으로 판정한다.
  - 운영 모델과 무관하게 실행할 때마다 따로 학습한다. 상세 내용은 `PATTERN_EXPERIMENT.md` 참고.

## Frontend

- 템플릿: `src/main/resources/templates/` (한국어). 공통 조각은 `fragments/head.html`, `fragments/navigation.html`.
- 페이지 템플릿은 다음 형태로 head를 불러오고 페이지 전용 스크립트만 추가한다.
  ```html
  <head th:replace="~{fragments/head :: head(~{::title}, ~{}, ~{::script})}">
  ```
- API 주소는 JS에 하드코딩하지 않고 `th:attr="data-url=@{...}"`로 전달한다.

### CSS (`static/css/`)

| 파일 | 내용 |
|------|------|
| `base.css` | 디자인 토큰(`:root` 변수), 다크 모드, 리셋 |
| `components.css` | 버튼, 폼, 패널, 표, 로또 공, 용지(sheet) 등 공통 컴포넌트 |
| `layout.css` | 사이드바, 모바일 드로어, 페이지 헤더 |
| `pages.css` | 화면별 스타일 |

- 색상은 반드시 토큰(`var(--accent)` 등)을 사용한다. 다크 모드는 `prefers-color-scheme`으로 시스템 설정만 따른다.
- 포인트 색은 인디고, 폰트는 Pretendard(CDN).
- 로또 공 색상은 동행복권 체계: 1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록 (`.ball-group-1`~`5`).
- 반응형 기준: 1080px 이하 1열, 768px 이하 모바일 드로어, 600px 이하 모바일 폼·표.

### JS (`static/js/`)

프레임워크 없이 IIFE 모듈로 작성한다.

- `common.js`: `window.LottoCommon` (`fetchJson`, `createMessenger`, `setupTabs`, `notify`)
- 요청 실패·완료 알림은 `notify(message, { type: 'error' | 'success' | 'info', title })` 토스트로 표시한다. 화면 안 상태 문구(`status-text`, `status-banner`)는 진행 중 안내에만 쓴다.
- `lotto-sheet.js`: `window.LottoSheet` (`validHistory`, `ball`, `sheet`)
- `sidebar.js`: 사이드바 접기, 모바일 드로어
- `history.js`, `prediction.js`, `register.js`, `validation.js`: 화면별 스크립트

## Working Notes

- 사용자가 직접 빌드·테스트한다. 수정 후 Gradle 컴파일이나 테스트를 실행하지 않는다.
- 요청 없이 커밋하지 않는다.
- 관련 문서: `LOTTO_PREDICTION_PLAN.md` (초기 계획서, 일부 내용은 현재 구조와 다름), `PATTERN_EXPERIMENT.md` (모델 저장·갱신, 시간순 검증 방법과 검증 기록).
