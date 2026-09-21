# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

로또 6/45 당첨 이력을 수집·조회하고, Smile ML(RandomForest)로 다음 회차 번호를 예측·검증하는 웹 애플리케이션.

- Spring Boot 4.0.1, Java 17, Gradle 9.2.1
- Spring Data JPA + MariaDB, Thymeleaf(SSR), Lombok
- Smile 3.1.1 (머신러닝)
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

- `lotto.model.check-interval-ms` (기본 3600000): 예측 모델(A·B·E)의 이력 변경 점검 주기.
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
| `domain/` | `LottoHistory` 엔티티, `PrizeRank`(등수별 당첨 정보 묶음), `LottoRules`(번호 범위·연속 회차 검증, 신뢰 회차 기준 `FIRST_TRUSTED_DRAW`) |
| `dto/` | 요청/응답 record |
| `event/` | `LottoHistoryChanged` (이력 변경 이벤트) |
| `exception/` | 도메인 예외 |
| `importer/` | 동행복권 API 적재 (`DonghaengApiClient`) |
| `ml/` | 특징 추출, 모델 학습·저장·추론 |
| `repository/` | Spring Data JPA 리포지토리 |
| `config/` | `SchedulingConfig` (`@EnableScheduling`) |

### 당첨 이력 (`lotto_history`)

`LottoHistory`는 테이블 28개 컬럼을 그대로 매핑한다. 테이블 정의는 `LOTTO.erd`에 있고 스키마를 직접 관리하므로, **컬럼을 바꾸면 ERD·실제 테이블·엔티티를 함께 고친다.**

**836회(2018-12-08) 이후만 동기화한다.** 그 이전은 로또를 위탁 운영한 기관이 달라 판매금액·당첨자 수 집계와 구매 환경이 지금과 다르다. 인기도를 같은 자로 잴 수 없으므로 **인기도 관련 측정·학습에 옛 회차를 다시 끌어오지 않는다.** 당첨 번호 자체는 운영 기관과 무관하므로, 번호만 쓰는 계산(A·B 특징, 동반출현 통계, 적중률 검증)은 전 회차를 써도 된다.

이 경계는 문서 약속이 아니라 **코드가 강제한다**: `LottoRules.FIRST_TRUSTED_DRAW = 836`을 D의 학습(`PopularityPredictor.train`)과 검증 채점(`LottoValidationService.PopularitySamples.add`) 양쪽에서 거른다. 옛 회차를 동기화해도 D는 그 구간을 보지 않고, A·B·C·E와 적중률 검증은 그대로 전 회차를 쓴다.

- 항상 있는 값: `drw_no`(유니크), `win_no1`~`win_no6`, `bonus_no`, `created_date`.
- 동기화로 채우는 값: `draw_date`(yyyyMMdd 문자열), 등수별 `win_noN_co`/`_amt`/`_sum_amt`(1~5등 15개), `total_win_co`, `total_sell_amt`.
- 아직 받지 못한 값은 **0이 아니라 null**로 둔다. "당첨자 0명"과 구분되지 않으면 조합 인기도 계산이 조용히 틀어진다.
- `total_sell_amt ÷ 1,000`이 그 회차에 팔린 게임 수다. 등수별 당첨 인원 수와 함께 조합 인기도를 재는 재료다.
- 등수별 값은 파라미터 15개를 늘어놓지 않도록 `List<PrizeRank>`(1~5등 순서)로 주고받고, 저장은 개별 컬럼에 한다. 동기화는 회차가 있으면 갱신(빈 값은 기존 값 유지), 없으면 추가한다.

### 화면 (HomeController)

| 경로 | 템플릿 | 내용 |
|------|--------|------|
| `GET /` | `index` | 당첨 이력 조회 |
| `GET /lotto/prediction` (`/prediction` 리다이렉트) | `prediction` | 점수 모델(A)·확률 모델(B)·동반출현 3개(C)·동반출현 4개(D)·인기도 모델(E)로 다음 회차 1게임씩, 모델 상태 |
| `GET /validation` | `validation` | 시간순 검증 (A·B·C·D 적중률을 무작위와 비교, E 인기도 예측 정확도) |

### REST API

| API | 설명 |
|-----|------|
| `GET /api/lotto/draws` | 등록된 회차 번호 목록 |
| `GET /api/lotto/history?fromDrawNo=&toDrawNo=` | 회차 범위 당첨 이력 |
| `GET /api/lotto/recommend` | v1 확률 모델 5게임 추천 (UI 호출처 없음, 유지 대상) |
| `GET /api/lotto/prediction` | A·B·C·D·E 1게임씩 (A·B·E 중 실패한 모델은 그 게임만 `available=false`) |
| `GET /api/lotto/prediction/status` | 학습 진행 여부와 모델별 상태 |
| `POST /api/lotto/prediction/train` | 학습 모델(A·B·E) 변경 확인·재학습 요청 (202) |
| `POST /api/lotto/validation?testDraws=` | 시간순 검증 시작 (백그라운드, 202 / 실행 중이면 409) |
| `GET /api/lotto/validation` | 마지막 검증 상태·진행 단계·결과(A·B·C·D 적중률 + `popularity` E 인기도 예측 정확도), 실행 가능한 최대 회차 수(`maxTestDraws` = 이력 수 − 50, 최대 500) |
| `GET /system/sync?startNo=&endNo=` | 동행복권 API 동기화 (범위를 한 번에 받아 신규 추가·기존 갱신, 두 회차 모두 필수) |

예외 응답은 `ApiExceptionHandler`가 `ErrorResponse(message)`로 변환한다: `InvalidLottoDataException`·잘못된 요청 본문 → 400, `ValidationInProgressException`·무결성 위반 → 409, `ModelNotReadyException` → 503.

### 예측 모델

번호 예측 화면(`LottoPredictionService`)은 A·B·E 모델과 C·D 집계로 1게임씩, 모두 5게임을 만든다. A·B는 Smile RandomForest로 1~45번 공마다 다음 회차 출현 여부를 학습한다. **다섯 게임의 당첨 확률은 모두 같다.** A·B는 적중률을 노리고(검증 결과 무작위와 차이 없음), C·D는 사람들이 많이 고르는 쪽, E는 덜 고르는 쪽이다.

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
- **동반출현 게임 (C·D)** (`CoOccurrenceGameGenerator`): 학습 없이, 당첨 번호에 가장 자주 함께 나온 조합을 고르고 남은 자리를 무작위로 채운다. C는 3개 + 무작위 3개, D는 4개 + 무작위 2개.
  - 동행복권 통계 화면의 "동반 출현 번호 통계"와 **같은 계산**이다 (회차마다 당첨 번호 6개에서 k개 부분집합을 전부 세고, 보너스 번호는 쓰지 않는다). 공식 API `selectLt645SetNo.do?countType=3|4&srchBnsYn=N&srchStrLtEpsd=&srchEndLtEpsd=`로 대조할 수 있고, 구간을 836~1242로 맞추면 값이 정확히 일치한다.
  - 집계는 이력이 있는 회차 전부로 한다. 번호만 쓰는 계산이라 `FIRST_TRUSTED_DRAW`를 적용하지 않는다. 407회차 기준 1.4만 번이면 다 세므로 **집계 테이블도 캐시도 두지 않는다.**
  - 최다 조합이 여럿이면 그중 무작위로 고른다. 407회차(836~1242) 기준 C는 5회짜리 5개 동점(`16-25-31`·`21-30-35`·`3-15-22`·`30-31-41`·`7-19-23`), D는 3회짜리 `1-3-24-27` 하나라 **D는 순위가 바뀌기 전까지 같은 4개가 계속 나온다.**
  - **당첨 확률은 오르지 않는다.** 출현 횟수 분포가 우연의 기대값과 거의 같다 (3개 조합이 5회 나온 것: 실제 5개 / 기대 4.1개. 4개 조합이 3회 나온 것: 실제 1개 / 기대 1.6개). 많이 나온 조합이 있는 것은 조합이 14,190개라 누군가는 그만큼 나와야 하기 때문이다.
  - 오히려 **사람이 많이 고르는 쪽**이다 (406회차: 동반출현 점수 상위 25% 회차가 하위 25%보다 약 2% 더 붐빔, r=0.155 t=3.2). 통계 사이트가 알려주니 실제로 그쪽을 산다. E와 반대 방향에 나란히 두어 대비를 보여 주는 것이 이 두 게임의 쓰임새이고, 화면 문구도 그렇게 적는다.
  - 옛 C(생일 조합 제외, `UnpopularGameGenerator`)는 **삭제했다.** 거르는 것이 전체 조합의 9%뿐이라 기대 이득이 −0.35%(±0.18%p)로 무작위와 사실상 같았다.
- **인기도 재는 법** (C·D·E 공통 기준): 등수별 `실제 당첨자 ÷ 기대 당첨자`. 기대 당첨자 = 판매 게임 수 × 해당 등수 조합 수 ÷ 8,145,060 (조합 수: 1등 1, 2등 6, 3등 228, 4등 11,115, 5등 182,780). 1등은 표본이 작아 신호가 묻히므로 5등·3등으로 판단한다.
- **안 쓰기로 한 제외 규칙** (동행복권 구간 406회차 실측, 옛 C의 근거였다. 원지수와 추세 제거값이 같은 결과를 냈다):

    | 규칙 | 해당 회차 | 5등 인기도 | 3등 인기도 | 판정 |
    |---|---|---|---|---|
    | 6개 모두 31 이하 | 34 | **+4.2%** (t=4.4) | **+10.0%** (t=3.4) | 붐비는 것은 맞으나 이득이 없어 폐기 |
    | 직전 회차와 2개 이상 겹침 | 66 | **−2.1%** (t=−5.0) | **−5.6%** (t=−5.2) | 덜 붐빔 — 제외하면 손해 (E가 특징으로 학습한다) |
    | 4개 이상 등간격 | 6 | −3.4% | −8.0% | 표본 6건, 판단 보류 |
    | 3개 이상 연속 | 19 | −2.2% | −2.3% | 차이 없음 |
    | 용지 한 줄 4개 | 17 | −0.1% | +4.4% | 차이 없음 |

  - 생일 규칙 말고 **넷은 방향이 거꾸로였다.** 유의하지 않은 것까지 모두 "덜 붐빔" 쪽 부호다. 사람들이 이상하게 생긴 조합을 피하니 당연한데, 옛 C는 그런 조합을 골라 제외하고 있었다. 다시 넣지 않는다.
- **인기도 모델 (E)** (`LottoPopularityModelService`, `PopularityPredictor`, `CombinationFeatures`): C·D와 반대로 **덜 붐비는 쪽**을 노린다. 어떤 조합이 붐비는지 사람이 규칙으로 정하지 않고 데이터에서 배운다.
  - 학습 데이터는 회차별 (당첨 조합의 생김새 → 그 회차 5등 인기도 지수). 당첨 조합은 매 회차 무작위로 정해지므로 조합 공간에서 고르게 뽑힌 표본이다.
  - 특징 9개(`CombinationFeatures`): 합계, 홀수 개수, 31 이하 개수, 최장 연번, 간격 표준편차, 십의 자리 묶음 수, 같은 끝자리 최대 개수, 용지 한 줄·칸 최대 개수, 직전 회차 겹침 개수. 표본이 회차 수뿐이라 더 늘리면 과적합한다.
  - 마지막 `last_draw_overlap`만 조합 바깥을 본다. 직전 회차와 2개 이상 겹치는 조합이 덜 붐빈다는 실측(위 "안 쓰기로 한 제외 규칙" 표)을 사람이 규칙으로 박지 않고 가중치를 데이터가 정하게 둔 것이다. 이 때문에 학습·예측 양쪽에 바로 앞 회차 번호가 필요하다. 앞 회차가 이력에 없는 회차(보통 가장 오래된 1건)는 학습에서 빠지고, 겹침은 번호만 쓰는 계산이라 앞 회차가 836회 미만이어도 그대로 쓴다.
  - 특징을 표준화한 뒤 능선 회귀(λ=1)로 학습한다. 트리 모델을 쓰지 않는 이유는 표본이 적고 특징 간 상관이 크기 때문이다. **계수를 하나씩 해석하면 안 된다** (합계와 31 이하 개수처럼 상관이 큰 특징끼리 부호가 뒤집힌다). 후보 순위를 매기는 데만 쓴다.
  - 학습 대상은 `LottoRules.FIRST_TRUSTED_DRAW`(836회) 이후로 제한된다. 창(`TRAINING_WINDOW`)을 자르기 전에 거르므로, `/system/sync?startNo=1`로 옛 회차를 넣어도 창이 836회 밑으로 내려가지 않는다.
  - `TRAINING_WINDOW = 600`은 **아직 한 번도 작동한 적이 없고 검증되지도 않았다.** 836회 이후가 406건뿐이라 늘 전체를 학습한다. 이 값을 고른 근거였던 "초기 200회차는 다른 시장" 측정은 동행복권 이전 데이터로 낸 것이라 폐기했다. 신뢰 구간이 600회를 넘는 2030년쯤까지 상한으로만 남는다.
  - 생성은 후보 200개 중 예측 인기도가 가장 낮은 것을 고른다. 전체에서 최솟값을 찾으면 하나로 고정돼 매번 같은 조합만 나온다.
  - **뽑히는 번호는 높은 쪽으로 치우친다** (합계 평균 203, 무작위 기대 138). 사람들이 생일 때문에 낮은 번호를 고르니 붐비는 곳을 피하면 필연적이다. 합계 구간별 5등 인기도 실측(406회차): ~130이 +2.1%, 130~160이 −0.8%, 160~190이 −2.5%, 190 이상이 −3.9%(±0.6%p)로 **높은 합계에서도 효과가 꺾이지 않는다.** 그래서 후보 수를 줄이거나 합계 상한을 두지 않는다.
  - **실측** (검증 화면, 946~1242회 297회차를 각각 직전 이력만으로 학습): 예측-실제 상관계수 **0.674**, 보정 기울기 **1.004**. 덜 붐빌 것으로 예측한 하위 25% 회차의 실제 인기도 **0.9713**, 상위 25% **1.0327**.
  - 겹침 특징을 넣기 전(특징 8개, 296회차)은 0.639 / 1.05 / 0.9715 / 1.0306이었다. 상관계수가 오르고 기울기가 1에 더 가까워졌지만 **같은 데이터를 한 번 더 잰 것이라 차이가 우연인지는 가릴 수 없다.** 겹침 특징이 확실히 낫다고 말하려면 이후 새로 쌓인 회차로 다시 확인해야 한다.
  - 기울기가 1 근처라 **예측값을 그대로 읽어도 된다.** 다만 근거가 297회차 한 번뿐이다. 예전에 적어 둔 "기울기 0.73, 실제 이득 약 5%"는 동행복권 이전 데이터를 섞어 낸 값이라 폐기했다. 어느 쪽이든 **적중률과는 무관하다.**
  - 판매금액이나 5등 당첨자 수가 없으면(동기화 전) 학습하지 못하고 그 게임만 `available=false`가 된다. 지문에 판매금액·5등 당첨자 수를 포함하므로(`HistoryFingerprint.ofPopularity`) 동기화로 그 값만 채워져도 다시 학습한다.
  - 특징·학습 방식을 바꾸면 `LottoPopularityModelService.MODEL_VERSION`도 바꾼다. 이력이 그대로여도 지문이 달라져 다음 점검 때 다시 학습한다.
  - `RandomGameGenerator`는 화면 게임이 아니라 **후보를 뽑는 유틸**이다 (정적 `draw(Random)` 하나). E가 후보 200개를 뽑을 때 쓴다. 무작위 기준선 게임은 화면에서 뺐다 — 무작위와의 비교는 검증 화면의 초기하분포 이론값이 더 정확하게 해 준다.
- **학습 시점** (`LottoModelTrainingService`): 시작 시, `LottoHistoryChanged` 이벤트(커밋 후), `lotto.model.check-interval-ms` 주기 점검 때 한 백그라운드 스레드에서 A·B·E를 차례로 확인한다.
  - 모델마다 회차·당첨 번호 지문(`HistoryFingerprint`)이 마지막 학습 때와 다를 때만 전체 이력으로 다시 학습한다.
  - 예측·추천 요청은 학습하지 않는다. 학습 실패 시 오류만 기록하고 기존 학습 결과를 유지한다.
  - 이력을 저장하는 코드는 `LottoHistoryChanged` 이벤트를 발행해야 한다.
- **시간순 검증** (`LottoValidationService`, `RandomMatchStatistics`): 최근 20~500회차를 각각 그 이전 이력만으로 맞혀 A·B·C·D를 비교한다. 357회차(886~1242회) 검증에서 **A·B 모두 무작위와 차이가 없었다** (평균 일치 A 0.782 / B 0.824, 무작위 0.800 — 1 표준오차 안. 옛 C(생일 제외)는 0.759였다). 새 C·D는 아직 검증하지 않았고, 역시 차이가 없을 것으로 본다.
  - B의 "3개 이상 일치 4.21%"(무작위 2.38%)는 눈에 띄지만 **신호가 아니다.** 표에 p값이 6개 있어 전부 무작위여도 하나가 그만큼 낮게 나올 확률이 15%고, 주 지표인 평균 일치 개수는 p=0.30이며, 15회가 전부 정확히 3개이고 4개 이상은 0회다. 이 칸은 참고용이라는 것을 잊지 말 것.
  - 특징은 한 번만 계산해 공유하고, 20회차(`REFIT_INTERVAL`)마다 다시 학습한다. 백그라운드 스레드 하나에서 한 번에 한 건만 실행하며 상태는 메모리에 마지막 1건만 둔다.
  - 무작위 기준은 시뮬레이션이 아닌 초기하분포 이론값(평균 0.8개, 3개 이상 약 2.38%)이다. 평균 일치 개수 합계의 정확한 분포로 단측 p값을 구하고, 0.05 ÷ 게임 수(지금 4게임이므로 1.25%)보다 작을 때만 "무작위보다 나음"으로 판정한다.
  - 운영 모델과 무관하게 실행할 때마다 따로 학습한다. 상세 내용은 `PATTERN_EXPERIMENT.md` 참고.
  - **E는 같은 표에 넣지 않고 따로 잰다** (`PopularityStatistics`, 화면의 "인기도 예측 검증" 항목). E가 노리는 것은 적중률이 아니라 당첨금을 나눌 사람 수라 평균 일치 개수로는 잴 수 없다.
    - 생성한 조합의 실제 인기도는 알 수 없으므로(당첨되지 않았으니 5등 당첨자 수도 없다), 대신 **그 회차 실제 당첨 조합의 인기도를 직전 이력만으로 학습한 모델이 얼마나 맞히는지** 잰다. 당첨 조합은 무작위로 정해지므로 모델이 처음 보는, 고르게 뽑힌 표본이다.
    - 예측-실제 상관계수, 보정 기울기, 예측 하위 25%와 상위 25% 회차의 실제 평균 인기도를 낸다. 상관계수 0 가정의 단측 t 검정(자유도 n−2) p값이 0.05보다 작을 때만 관계가 있다고 본다. 검정할 가설이 하나라 게임 수로 나누지 않는다.
    - 판매금액이나 5등 당첨자 수가 없는 회차는 건너뛴다. 남은 회차가 20건 미만이면 이 항목만 "잴 수 없음"으로 두고 적중률 검증은 그대로 진행한다.
    - 무작위 게임을 만들어 함께 비교하지는 않는다. 초기하분포 이론값이 시뮬레이션한 무작위 게임보다 정확하다.

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
- 요청 실패·완료 알림은 `notify(message, { type: 'error' | 'success' | 'info', title })` 토스트로 표시한다. 화면 안 상태 문구(`status-text`)는 진행 중 안내에만 쓴다.
- `lotto-sheet.js`: `window.LottoSheet` (`validHistory`, `ball`, `sheet`)
- `sidebar.js`: 사이드바 접기, 모바일 드로어
- `history.js`, `prediction.js`, `validation.js`: 화면별 스크립트

## Working Notes

- 사용자가 직접 빌드·테스트한다. 수정 후 Gradle 컴파일이나 테스트를 실행하지 않는다.
- 요청 없이 커밋하지 않는다.
- 테이블 정의는 `LOTTO.erd`에 있다. 컬럼을 바꾸면 ERD도 같이 고친다.
- 남은 작업·결정 대기 항목은 `TODO.md`에 둔다. 이 파일에는 **지금 코드가 이런 상태다**만 적고, 앞으로 할 일은 적지 않는다.
- `AGENTS.md`는 이 파일의 사본이다 (1·3행의 도구 이름만 다름). 이 파일을 고치면 함께 갱신한다.
- 관련 문서: `TODO.md` (남은 작업), `PATTERN_EXPERIMENT.md` (모델 저장·갱신, 시간순 검증 방법과 검증 기록).
