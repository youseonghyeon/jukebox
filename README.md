# Jukebox Service

대량의 데이터를 적재하고 효율적으로 조회할 수 있는 Jukebox Service를 구현합니다.

## 기술 스택
| Category        | Technology        | Version         |
|-----------------|-------------------|-----------------|
| **Language**    | Java              | 21 (LTS)        |
| **Framework**   | Spring Boot       | 3.5.9 (WebFlux) |
| **Database**    | MySQL             | 8.0             |
| **Persistence** | Spring Data R2DBC | -               |
| **Build Tool**  | Gradle            | -               |

## 실행 방법

1. **실행 전 초기 설정**
    ```yaml
    # src/main/resources/application.yml
    jukebox:
        dataset:
            enabled: true # 기초 데이터 적재 활성화
            location: ../data/spotify_dataset.json # 기초 데이터 파일 경로
    ```

2. **데이터베이스 컨테이너 생성**
    ```bash
    # DB 컨테이너 생성 및 실행 (mysql port: 13306:3306 매핑)
    $ docker-compose up -d
    ```
3. **애플리케이션 실행**


## 테스트 관련 사항
1. **단위 테스트 실행 시 POJO 검증**
2. **통합 테스트 시 TestContainer 기반의 MySQL 컨테이너 활용**


---

# 기술적 의사결정 및 구현 상세

### 1. 기초 데이터 처리 (Data Processing)
> 기초 데이터 파일을 효율적으로 읽어 관계형 데이터베이스에 저장

* **구현 요구사항**
    * 메모리 사용량을 최소화
    * 인덱싱 전략 고려

* **구현 상세**
    * 대용량 데이터 스트리밍 및 배치(Callback) 처리
      * 메모리 최적화: Jackson Streaming API와 MappingIterator 를 활용하여 메모리 점유 최소화
      * 청크 기반 프로세싱: JsonBatchReader를 통해 설정된 batchSize 단위로 데이터를 분할 로드하며, 콜백 구조를 통해 읽기와 쓰기 로직을 결합도 낮게 설계
    * R2DBC 기반의 고성능 Write 전략
      * Bulk Insert 최적화: R2DBC DatabaseClient의 Multi-row Insert 기능을 수동 구현, R2DBC의 saveAll() 메서드보다 성능 향상 (6분 -> 1분)
      * PK 생성 전략: batch Insert 성능을 최적화하고, 연관(FK) 관계 설정을 단순화하기 위해 Songs 테이블 PK 생성에 Tsid활용
    * 리액티브 환경의 실행 모델 최적화
      * 리소스 격리: 대량의 데이터 처리 작업이 시스템 전체의 반응성을 저해하지 않도록, Java21의 (싱글) 가상 스레드 전용 환경 구성
      * 블로킹 제어: 메모리 사용량을 최소화 하기 위해, 파일 읽기 작업과 DB 쓰기 작업을 순차 동기 처리
    * 데이터 무결성 보장
      * 원자성 보장: TransactionalOperator를 활용하여 부모와 자식 엔티티 간의 데이터 정합성을 확보했으며, 예외 발생 시 배치 단위 롤백 처리
      * 병렬성 제어: flatMap의 concurrency 옵션을 활용하여 데이터베이스 커넥션 풀의 부하 조절
    * 데이터 모델 설계 및 인덱싱 전략 고려
      * 정규화된 관계형 모델: songs(노래 정보), song_metrics(노래 메트릭), similar_songs(유사 노래 매핑) 테이블로 구성 (1:1, 1:N 관계 반영)
      * 인덱싱 전략: 발매 연도 별 조회 성능을 향상하기 위해 songs 테이블의 release_year 컬럼 생성 및 인덱스 추가

* **처리 결과** (batchSize: 1000 기준 / M4 맥북 환경)
    * 평균 메모리 사용량: 250MB
    * 총 처리 시간: 64,706 ms (약 1분 5초)

### 2-0. 데이터 모델 설계 변경
> 연도 & 가수별 발매 앨범 수 조회 API 개발 진행중 발견된 성능 개선을 위한 데이터 모델 설계 변경
* **변경 사항**
    * song_statistics 테이블 추가
        * 가수명과 발매 연도별 앨범 수를 미리 집계하여 저장
        * 앨범 개수 연산은 데이터 생성 시점으로 이관하여 조회 성능 최적화
        * 인덱스 전략: (release_year desc, artists(100)) 복합 인덱스 추가
    * songs 테이블 변경 
        * 기존 songs 테이블의 집계용 컬럼(release_year) 제거
        * 인덱스 제거: (artist_name, release_year desc) 복합 인덱스 제거

### 2-1. API 구현
> 연도 & 가수별 발매 앨범 수 조회 API

* **구현 요구사항**
    * 연도 & 가수별 발매 앨범 수 조회 API 개발, 페이징 기능 포함

* **구현 상세**
    * ~~초기 구현~~
        * songs 테이블에서 연도 & 가수별 발매 앨범 수를 실시간 집계하여 조회
        * 문제점: 발매 앨범 수 집계 시 Full-Scan이 발생하여 응답 속도 저하 (2,273ms)
    * 기능 변경
        * 조회용 테이블(song_statistics) 추가 및 데이터 모델 설계 변경
        * 결과: 응답 속도 개선 (2,273ms -> 30ms)
    * 기능 개발
        * R2DBC Criteria API를 활용한 동적 쿼리 작성
        * 정렬 및 페이징 기능 구현
* **처리 결과**
    * 조회 API 응답 속도: 30ms

### 3. API 구현
> 노래별 좋아요 API

* **구현 요구사항**
    * 좋아요 증가 API
    * 최근 1시간 좋아요 상위 10곡 조회 API

---
