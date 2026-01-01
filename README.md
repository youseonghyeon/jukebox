# Jukebox Service

대량의 데이터를 적재하고 효율적으로 조회할 수 있는 Jukebox Service를 구현합니다.

## 기술 스택

| Category        | Technology        | Version         |
|-----------------|-------------------|-----------------|
| **Language**    | Java              | 21 (LTS)        |
| **Framework**   | Spring Boot       | 3.5.9 (WebFlux) |
| **Database**    | MySQL             | 8.0             |
| **Cache**       | Redis             | 7.0             |
| **Persistence** | Spring Data R2DBC | -               |
| **Build Tool**  | Gradle            | -               |

## 실행 방법

1. **초기 환경 세팅**
    * 초기 데이터 적재 를 위해 데이터 파일 경로`jukebox:dataset:location` 를 설정해야합니다.
    * 데이터 적재가 필요하지 않은 경우, `jukebox:dataset:enabled` 옵션을 `false`로 설정합니다.

```yaml
# src/main/resources/application.yml
jukebox:
  dataset:
    enabled: true # 기초 데이터 적재 기능 활성화
    location: ../data/spotify_dataset.json # 기초 데이터 파일 경로 설정
    batch-size: 1000 # 데이터 적재 시 배치 크기 설정
  like:
    strategy: redis # 좋아요 쓰기 버퍼링 전략 (options: redis=분산환경, memory=단일 인스턴스)
    write-buffer:
      cron: "0 0/5 * * * *" # 5분마다 좋아요 버퍼를 DB에 반영
```

2. **데이터베이스 컨테이너 생성**
    * 애플리케이션 실행 시 필요한 MySQL과 Redis 컨테이너를 Docker Compose를 통해 생성 및 실행합니다.

```bash
# DB 컨테이너 생성 및 실행 (MySQL: 13306, Redis: 16379)
# 프로젝트 루트 디렉토리에서 아래 명령어 실행
$ docker-compose -f docker-compose-local.yml up -d
```

3. **애플리케이션 실행**
    * 애플리케이션 실행 시점에 schema.sql이 자동으로 수행되어 데이터베이스 테이블이 생성됩니다.
   
4. **요청 테스트**
    * Swagger UI: 애플리케이션 실행 후 아래 주소에서 API 명세 확인 및 테스트가 가능합니다
    * http://localhost:8080/swagger-ui/index.html

5. **실행 종료 후 데이터베이스 컨테이너 중지**
```bash
# DB 컨테이너 중지 및 제거 (볼륨 데이터도 함께 삭제됩니다)
$ docker-compose -f docker-compose-local.yml down
```

## 테스트 실행 관련 사항
* 테스트 실행을 위해 Docker 환경이 반드시 필요합니다. (MySQL, Redis 컨테이너 자동 구성)

---

# 기술적 의사결정 및 구현 상세

### 1. 기초 데이터 처리 (Data Processing) [#1](https://github.com/youseonghyeon/jukebox/pull/1)

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
        * PK 생성 전략: batch Insert 성능을 최적화하고, 연관(FK) 관계 설정을 단순화하기 위해 Songs 테이블 PK 생성에 TSID활용
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
    * 총 적재 row: 2,490,260 rows
    * 총 처리 시간: 64,706 ms (약 1분 5초)

### 2-1. 데이터 모델 설계 변경 [#2](https://github.com/youseonghyeon/jukebox/pull/2)

> 연도 & 가수별 발매 앨범 수 조회 API 개발 진행중 발견된 성능 개선을 위한 데이터 모델 설계 변경

* **변경 사항**
    * song_statistics 테이블 추가
        * 가수명과 발매 연도별 앨범 수를 미리 집계하여 저장
        * 앨범 개수 연산은 데이터 생성 시점으로 이관하여 조회 성능 최적화
        * 인덱스 전략: (release_year desc, artists(100)) 복합 인덱스 추가
    * songs 테이블 변경
        * 기존 songs 테이블의 집계용 컬럼(release_year) 제거
        * 인덱스 제거: (artist_name, release_year desc) 복합 인덱스 제거

### 2-2. API 구현 [#3](https://github.com/youseonghyeon/jukebox/pull/3)

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

### 3. API 구현 [#4](https://github.com/youseonghyeon/jukebox/pull/4)

> 노래별 좋아요 API

* **구현 요구사항**
    * '좋아요' 기능에 대한 모델링 및 '좋아요' 를 증가시킬 수 있는 API를 구현
    * 최근 1시간 동안 '좋아요' 증가 Top 10을 확인할 수 있는 API를 구현

* **구현 상세**
    * **데이터 모델링 및 인덱스 전략**
        * Schema: songs(좋아요 합계), song_likes(이력 관리) 테이블 분리 설계
        * 인덱스 전략 1: song_likes 테이블에 (song_id, liked_at desc) 복합 인덱스 추가 (중복 좋아요 방지 쿼리 최적화)
        * 인덱스 전략 2: song_likes 테이블에 (created_at) 단일 인덱스 추가 (기간별 집계(Range Scan) 성능 확보)
    * **좋아요 증가 API 구현**
        * 좋아요 증가 처리 흐름
            1. 히스토리성 테이블(song_likes)에 좋아요 기록 추가
            2. 기록 추가 성공 시, 버퍼를 활용하여 좋아요 합계 계산 (아래 '모놀리스/분산 환경의 데이터 정합성 보장' 설명 참조)
            3. 일정 주기마다 버퍼 데이터를 DB에 일괄 반영
        * 스케줄러를 통해 일정 주기마다 버퍼 데이터를 DB에 일괄 반영
    * **환경별 정합성 및 가용성 확보 전략**
        * 모놀리스 환경: Memory를 활용한 내부 버퍼링 전략 적용
            * Lock-Free: `AtomicReference`와 `ConcurrentHashMap`, `LongAdder`를 활용하여 락 경합 없는 카운팅 구현
            * Atomic Snapshot: 객체 교체(Swap) 방식을 통해 버퍼 스위칭 시 데이터 유입 공백 제거
            * Fault Tolerance: 서버 종료 시`@PreDestroy` 잔여 데이터를 DB에 반영하고, 실패 시 로컬 파일(Outbox)로 백업하여 데이터 유실 원천 차단
        * 분산 환경: Redis를 활용한 외부 버퍼링 전략 적용
            * Atomic Command: `HINCRBY`로 원자적 증가 처리 및 `RENAME`을 통한 안전한 스냅샷 생성
            * Distributed Lock: `SETNX`를 활용하여 다중 인스턴스 환경에서 스케줄러의 중복 실행 방지
            * Self-Healing: 서버 재기동 시 미처리된 Redis 스냅샷을 감지하여 DB에 반영하는 복구 로직 구현
    * **최근 1시간 동안 '좋아요' 증가 Top 10 API 구현**
        * R2DBC Flux 스트림을 활용하여 논블로킹 방식으로 Top 10 집계 쿼리 수행 후 결과 반환

### 4. API 응답값 수정 및 장애 복구 개선 [#5](https://github.com/youseonghyeon/jukebox/pull/5)
> API 응답값 수정 및 장애 복구 개선

* **구현 상세**
    * **최근 1시간 동안 '좋아요' 증가 Top 10 API 응답값 추가**
      * AS-IS: songId와 likeCount만 반환
      * TO-BE: songId, title, artist, album, likeCount 반환
      * 변경 사유: 클라이언트의 추가 조회 요청 방지 및 응답값 완결성 확보
    * **장애 복구 개선**
      * 좋아요 메모리 버퍼링 전략 개선
        * DB 장애 발생 시 `Snapshot` 에 대한 복구 로직 추가
        * DB 장애 환경에서 서버 다운에 대한 Outbox 파일 백업 안정화
    * 누락된 song_likes 테이블 FK(song_id) 제약조건 추가

---
