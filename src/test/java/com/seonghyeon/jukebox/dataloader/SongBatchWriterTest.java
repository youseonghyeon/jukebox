package com.seonghyeon.jukebox.dataloader;

import com.seonghyeon.jukebox.dataloader.dto.SimilarSongDto;
import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import com.seonghyeon.jukebox.repository.SimilarSongRepository;
import com.seonghyeon.jukebox.repository.SongMetricsRepository;
import com.seonghyeon.jukebox.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SongBatchWriterTest {

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private SongMetricsRepository songMetricsRepository;

    @Autowired
    private SimilarSongRepository similarSongRepository;

    @Autowired
    private SongBatchWriter songBatchWriter; // 테스트 대상

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @BeforeEach
    void cleanup() {
        // 외래키 제약 조건을 고려하여 자식 테이블부터 삭제
        var deleteSimilars = r2dbcEntityTemplate.getDatabaseClient().sql("DELETE FROM similar_songs").fetch().rowsUpdated();
        var deleteMetrics = r2dbcEntityTemplate.getDatabaseClient().sql("DELETE FROM song_metrics").fetch().rowsUpdated();
        var deleteSongs = r2dbcEntityTemplate.getDatabaseClient().sql("DELETE FROM songs").fetch().rowsUpdated();

        // 순차적으로 실행하여 데이터 삭제 완료 보장
        deleteSimilars.then(deleteMetrics).then(deleteSongs)
                .as(StepVerifier::create)
                .expectNextCount(1) // 각 삭제 쿼리가 성공했는지 확인 (결과값은 삭제된 로우 수)
                .verifyComplete();
    }

    // 1. MySQL 컨테이너 설정
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql")
            .withUrlParam("ssl", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--default-authentication-plugin=mysql_native_password"
            );

    // R2DBC 연결 설정 주입
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:mysql://%s:%d/%s?ssl=false",
                        mysql.getHost(), mysql.getFirstMappedPort(), mysql.getDatabaseName()));
        registry.add("spring.r2dbc.username", mysql::getUsername);
        registry.add("spring.r2dbc.password", mysql::getPassword);
    }

    @Test
    @DisplayName("대용량 데이터가 정상적으로 배치 Insert 되어야 한다")
    void bulkInsertTest() {
        // given: 테스트 데이터 1500개 생성
        int dataSize = 1500;
        List<SongDto> testData = createMockData(dataSize);

        // when: 배치 라이터 실행
        songBatchWriter.flushAll(testData);

        // then: Repository를 이용한 데이터 검증

        // 1. Songs 테이블 개수 확인
        songRepository.count()
                .as(StepVerifier::create)
                .expectNext((long) dataSize)
                .verifyComplete();

        // 2. Metrics 테이블 개수 확인
        songMetricsRepository.count()
                .as(StepVerifier::create)
                .expectNext((long) dataSize)
                .verifyComplete();

        // 3. Similar Songs 테이블 개수 확인
        similarSongRepository.count()
                .as(StepVerifier::create)
                .expectNext((long) dataSize)
                .verifyComplete();
    }

    @Test
    @DisplayName("삽입된 노래와 해당 노래의 메트릭 및 유사곡이 올바른 ID로 연결되어야 한다")
    void dataConsistencyTest() {
        // given: 딱 1개의 노래 데이터 생성
        List<SongDto> testData = createMockData(1);
        String targetTitle = testData.get(0).song();

        // when
        songBatchWriter.flushAll(testData);

        // then
        // 1. 노래 저장 확인 및 ID 추출
        songRepository.findAll()
                .filter(s -> s.title().equals(targetTitle))
                .next()
                .flatMap(song -> {
                    Long songId = song.id();
                    // 2. 해당 ID로 메트릭이 존재하는지 확인
                    return songMetricsRepository.findById(songId)
                            .zipWith(similarSongRepository.findAllBySongId(songId).collectList())
                            .map(tuple -> {
                                // 메트릭 존재 확인
                                assertThat(tuple.getT1()).isNotNull();
                                // 유사곡 개수 확인
                                assertThat(tuple.getT2()).hasSize(1);
                                return song;
                            });
                })
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("수천 건의 데이터를 동시에 처리해도 커넥션 이슈 없이 성공해야 한다")
    void highVolumeTest() {
        // given: 5000개 데이터
        int dataSize = 5000;
        List<SongDto> testData = createMockData(dataSize);

        // when
        songBatchWriter.flushAll(testData);

        // then
        songRepository.count()
                .as(StepVerifier::create)
                .expectNext((long) dataSize)
                .verifyComplete();
    }

    @Test
    @DisplayName("빈 리스트가 전달되면 아무런 작업도 수행하지 않고 종료되어야 한다")
    void emptyListTest() {
        // given
        List<SongDto> emptyList = Collections.emptyList();

        // when
        songBatchWriter.flushAll(emptyList);

        // then
        songRepository.count()
                .as(StepVerifier::create)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("삽입된 데이터의 각 필드 값이 DTO에 입력한 값과 정확히 일치해야 한다")
    void fieldDataIntegrityTest() {
        // given: 명확한 값을 가진 테스트 데이터 1개 생성
        SongDto originDto = new SongDto(
                "Test Artist", "Test Title", "Test Lyrics", "03:10",
                "joy", "Rock", "Test Album", "2023-12-25",
                "G Major", 128.0, -4.5, "4/4", "No",
                85, 90, 80, 70, 20, 10, 30, 5,
                1, 0, 1, 0, 0, 0, 0, 0, 0,
                List.of(new SimilarSongDto("Similar Artist", "Similar Title", 0.95), new SimilarSongDto("Another Artist", "Another Title", 0.89))
        );

        // when
        songBatchWriter.flushAll(List.of(originDto));

        // then: 각 테이블별로 한 건씩 뽑아서 필드 대조

        // 1. Songs 테이블 검증
        songRepository.findAll().next()
                .as(StepVerifier::create)
                .assertNext(s -> {
                    assertThat(s.artist()).isEqualTo("Test Artist");
                    assertThat(s.title()).isEqualTo("Test Title");
                    assertThat(s.album()).isEqualTo("Test Album");
                    assertThat(s.releaseDate()).isEqualTo(java.time.LocalDate.parse("2023-12-25"));
                    assertThat(s.releaseYear()).isEqualTo(2023);
                    assertThat(s.genre()).isEqualTo("Rock");
                    assertThat(s.lyrics()).isEqualTo("Test Lyrics");
                    assertThat(s.length()).isEqualTo("03:10");
                    assertThat(s.emotion()).isEqualTo("joy");
                    assertThat(s.totalLikes()).isEqualTo(0L);
                })
                .verifyComplete();

        // 2. Metrics 테이블 검증
        songMetricsRepository.findAll().next()
                .as(StepVerifier::create)
                .assertNext(m -> {
                    assertThat(m.musicalKey()).isEqualTo("G Major");
                    assertThat(m.tempo()).isEqualTo(128.0);
                    assertThat(m.loudnessDb()).isEqualTo(-4.5);
                    assertThat(m.timeSignature()).isEqualTo("4/4");
                    assertThat(m.explicit()).isEqualTo("No");
                    assertThat(m.popularity()).isEqualTo(85);
                    assertThat(m.energy()).isEqualTo(90);
                    assertThat(m.danceability()).isEqualTo(80);
                    assertThat(m.positiveness()).isEqualTo(70);
                    assertThat(m.speechiness()).isEqualTo(20);
                    assertThat(m.liveness()).isEqualTo(10);
                    assertThat(m.acousticness()).isEqualTo(30);
                    assertThat(m.instrumentalness()).isEqualTo(5);
                    assertThat(m.isParty()).isTrue();
                    assertThat(m.isStudy()).isFalse();
                    assertThat(m.isRelaxation()).isTrue();
                    assertThat(m.isExercise()).isFalse();
                    assertThat(m.isRunning()).isFalse();
                    assertThat(m.isYoga()).isFalse();
                    assertThat(m.isDriving()).isFalse();
                    assertThat(m.isSocial()).isFalse();
                    assertThat(m.isMorning()).isFalse();
                })
                .verifyComplete();

        // 3. Similar Songs 테이블 검증
        similarSongRepository.findAll() // .next()를 빼서 Flux<SimilarSongEntity>를 그대로 사용
                .as(StepVerifier::create)
                .assertNext(sim -> {
                    assertThat(sim.similarArtist()).isEqualTo("Similar Artist");
                    assertThat(sim.similarTitle()).isEqualTo("Similar Title");
                    assertThat(sim.similarityScore()).isEqualTo(0.95);
                })
                .assertNext(sim -> {
                    assertThat(sim.similarArtist()).isEqualTo("Another Artist");
                    assertThat(sim.similarTitle()).isEqualTo("Another Title");
                    assertThat(sim.similarityScore()).isEqualTo(0.89);
                })
                .verifyComplete();
    }

    // --- Helper ---
    private List<SongDto> createMockData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new SongDto(
                        "Artist " + i,
                        "Title " + i,
                        "Album " + i,
                        "2023-01-01",
                        "Pop",
                        "Lyrics...",
                        "3:30",
                        "Happy",
                        "C Major",
                        120.0,
                        -5.0,
                        "4/4",
                        "No",
                        50,
                        80, 70, 60, 10, 20, 30, 0,
                        1, 0, 0, 0, 0, 0, 0, 0, 0,
                        Collections.singletonList(new SimilarSongDto("Sim Artist", "Sim Title", 0.9))
                ))
                .toList();
    }
}
