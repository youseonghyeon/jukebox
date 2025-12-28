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

**1. 데이터베이스 컨테이너 생성**
```bash
# DB 컨테이너 생성 및 실행
$ docker-compose up -d
```
**2. 애플리케이션 실행**


---

## 기술적 의사결정 및 구현 상세

### 1. 기초 데이터 처리 (Data Processing)
> 기초 데이터 파일을 효율적으로 읽어 관계형 데이터베이스에 저장

* **구현 요구사항**
    * 메모리 사용량을 최소화
    * 인덱싱 전략 고려
  
### 2. API 구현
> 연도 & 가수별 발매 앨범 수 조회 API

* **구현 요구사항**
    * 페이징 기능 포함

### 3. API 구현
> 노래별 좋아요 API

* **구현 요구사항**
    * 좋아요 증가 API
    * 최근 1시간 좋아요 상위 10곡 조회 API

---
