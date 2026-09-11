# 순수 Java 기반 로또 1등 번호 머신러닝 예측 시스템 구현 계획서

## 1. 프로젝트 개요

* **프로젝트명**: `lotto-crawling`
* **개발 언어 및 프레임워크**: Java 17, Spring Boot 4.0.1, Gradle 9.2.1
* **주요 기술 스택**: Spring Data JPA, QueryDSL 5.0, MariaDB, Thymeleaf, Apache POI 5.2.5
* **목표**: 
  * 파이썬이나 유료 외부 AI API 없이 **순수 Java 17 환경 내에서 100% 무료로 구동**되는 로또 번호 예측 및 추천 시스템 구축.
  * 실제 역대 당첨 데이터를 수집하여 머신러닝 피처(Feature)를 구성하고, Java 머신러닝 모델(Smile)로 각 번호별 출현 확률을 계산하여 최적의 추천 번호 도출.

---

## 2. 핵심 아키텍처 및 동작 원리

```mermaid
flowchart TD
    subgraph DataLayer ["1. 데이터 수집 & 적재"]
        API[동행복권 공식 오픈 API 크롤러]
        EXCEL[엑셀 파일 수동 업로드 (Apache POI)]
        DB[(MariaDB: lotto_history)]
        API --> DB
        EXCEL --> DB
    end

    subgraph FeatureEngineering ["2. Java 피처 엔지니어링 (Feature Extractor)"]
        DB --> FE[1~45번 공별 데이터셋 생성\n- 최근 5/10/20회 빈도수\n- 연속 미출현 회차\n- 직전 회차와의 동반 출현 점수\n- 전체 누적 출현율]
    end

    subgraph MLLayer ["3. Java 머신러닝 모델 (Smile ML)"]
        FE --> ML[Random Forest / 분류 모델 학습]
        ML --> PROB[1~45번 차기 회차 출현 확률 산출 (0.0% ~ 100.0%)]
    end

    subgraph StrategyLayer ["4. 번호 추천 모드 (사용자 선택)"]
        PROB --> MODE1["모드 A: 순수 AI 예측 (Pure ML Top-6)\n- 확률 상위 1~6위 번호 그대로 추천"]
        PROB --> MODE2["모드 B: AI 가중치 + 밸런스 필터링\n- 확률 기반 가중 샘플링\n- 총합 (100~175) 필터\n- 홀짝/고저 비율 필터\n- 3연번 이상 배제 필터"]
    end

    subgraph PresentationLayer ["5. 웹 화면 (Thymeleaf UI)"]
        MODE1 --> UI[웹 대시보드]
        MODE2 --> UI[로또 볼 시각화 & 분석 통계 카드]
    end
```

---

## 3. 머신러닝 예측과 통계 필터 검증의 관계

### Q. 왜 머신러닝 결과에 통계 필터 검증을 함께 고려하는가?
1. **개별 확률 vs 6개 번호 조합의 현실성 차이**
   * 머신러닝 모델은 1번~45번 공 각각에 대해 "다음 회차에 나올 확률"을 독립적으로 계산합니다.
   * 단순히 확률 1위~6위를 뽑았을 때, 공 각각은 출현 확률이 높지만 **조합 전체로 보면 올짝수(`2, 4, 16, 28, 34, 40`)나 특정 구간 몰림(`40, 41, 42, 43, 44, 45`) 같은 기형적 조합**이 발생할 수 있습니다.
2. **다양성 확보 (5게임 생성 시)**
   * 로또 1장(5게임)을 생성할 때, 머신러닝 확률을 가중치 삼아 여러 번 추첨(가중 샘플링)하게 되며, 이때 상식적인 균형을 유지하기 위한 안전장치로 필터를 활용합니다.

### 💡 최종 제공 방식: 사용자 선택 옵션 (토글 제공)
* **[모드 A] 순수 AI 예측**: 어떠한 인위적인 통계 필터링도 거치지 않고 머신러닝 확률 1위~6위를 그대로 제공.
* **[모드 B] AI + 밸런스 필터**: 머신러닝 확률을 기반으로 하되, 6개 조합의 균형(총합, 홀짝비, 연번 제한 등)을 통계적으로 검증하여 추천.

---

## 4. 데이터 수집 방안 (이중 지원)

1. **동행복권 공식 오픈 API 자동 수집 (메인)**
   * URL: `https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={회차}`
   * Java `RestClient` / `RestTemplate`을 사용하여 1회차부터 최신 회차까지 한 번의 클릭으로 DB에 일괄 동기화.
2. **엑셀 수동 업로드 (서브)**
   * 동행복권 사이트에서 다운로드받은 엑셀 파일(`.xlsx`)을 `POST /system/manual/excel`로 업로드하면 Apache POI를 통해 파싱 후 DB 적재.

---

## 5. Java 머신러닝(ML) 엔진 상세 설계

### 5.1 Java 머신러닝 라이브러리: `Smile`
* `com.github.haifengl:smile-core:3.1.1` 사용 (Java 17 완벽 지원, 순수 Java 구현).
* 파이썬의 `scikit-learn`과 대등한 분류/회귀 알고리즘 및 확률 계산(`posteriori`) 지원.

### 5.2 피처 엔지니어링 (Feature Engineering)
과거 각 회차 $t$ 시점에서 1~45번 각 번호 $i$에 대해 다음 피처들을 벡터로 추출:
1. `freq_last_5`: 최근 5회 동안의 출현 횟수
2. `freq_last_10`: 최근 10회 동안의 출현 횟수
3. `freq_last_20`: 최근 20회 동안의 출현 횟수
4. `absence_streak`: 연속 미출현 회차 수 (최근 언제 마지막으로 나왔는지)
5. `co_occurrence_score`: 직전 회차($t-1$) 당첨 번호들과의 역대 동반 출현 빈도 합
6. `total_appearance_rate`: 전체 누적 출현율
* **Target ($Y$)**: 다음 회차($t$)에 해당 번호가 출현했는가 (1 또는 0)

### 5.3 학습 및 예측 프로세스
1. 최근 100~300회차 분량의 시점별 피처-타겟 데이터셋 생성.
2. **Random Forest Classifier** 모델 학습.
3. 가장 최신 회차 데이터를 피처로 변환 후 모델에 입력하여 **다음 회차의 1~45번 각 번호 출현 확률(0.0% ~ 100.0%)** 계산.

---

## 6. 단계별 상세 구현 로드맵

### Step 1. 빌드 설정 (`build.gradle`)
* `Smile` ML 라이브러리 추가:
  ```groovy
  implementation 'com.github.haifengl:smile-core:3.1.1'
  ```

### Step 2. 데이터 도메인 및 리포지토리 구축
* `LottoHistory.java` (Entity): 회차(`drwNo`), 추첨일(`drwDate`), 6개 당첨 번호(`drwtNo1`~`drwtNo6`), 보너스 번호(`bnusNo`), 당첨금 등.
* `LottoHistoryRepository.java`: JPA 리포지토리 (최신 회차 조회, 전체 회차 정렬 조회 등).

### Step 3. 데이터 수집 서비스 (`SystemService.java`)
* `syncFromDonghaengApi(int startDrwNo, int endDrwNo)`: 동행복권 API 호출 및 DB 저장.
* `parseExcelFile(MultipartFile file)`: POI 라이브러리로 엑셀 파일 읽어서 DB 저장.

### Step 4. Java 머신러닝 엔진 구현
* `LottoFeatureExtractor.java`: 과거 데이터로부터 피처 매트릭스($X$)와 라벨($Y$) 구성.
* `LottoMlPredictor.java`: Smile Random Forest 모델 학습 및 1~45번 확률 추론.
* `LottoRecommendationService.java`:
  * 순수 Top-6 조합 추출
  * 가중 샘플링 및 통계 필터(합계 100~175, 홀짝비, 고저비, 3연번 배제)를 통과한 5게임 세트 생성.

### Step 5. API 및 화면(UI) 연동
* `SystemController.java`:
  * `POST /system/sync`: 최신 회차 자동 동기화 API
  * `POST /system/manual/excel`: 엑셀 업로드 API
  * `GET /api/lotto/predict?mode=pure|filtered`: AI 번호 예측 API
* `index.html`:
  * 최신 회차 동기화 상태 표시 및 [데이터 동기화] 버튼
  * [순수 AI 예측] / [AI + 필터 추천] 모드 선택 스위치
  * [AI 번호 예측 생성] 버튼
  * 공 번호대별 시각화(노랑, 파랑, 빨강, 회색, 녹색 로또 볼 UI)
  * 번호별 AI 예측 확률 순위 및 통계 분석 결과 카드

---

## 7. 검증 및 테스트 계획

1. **컴파일 및 환경 검증**: JDK 17 환경에서 `./gradlew compileJava` 수행하여 Smile 및 JPA 의존성 충돌 검증.
2. **데이터 수집 테스트**: 동행복권 API 호출을 통해 실제 최신 회차 데이터가 MariaDB에 정상 적재되는지 확인.
3. **머신러닝 단위 테스트**: 1~45번 확률 추출이 정상적인 수치(합계 및 개별 확률)로 도출되는지 검증.
4. **통합 UI 테스트**: 웹 브라우저에서 동기화 $\rightarrow$ 예측 생성 $\rightarrow$ 번호 및 확률 분석 카드 표출 정상 동작 확인.
