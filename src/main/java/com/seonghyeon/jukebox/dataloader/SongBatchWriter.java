package com.seonghyeon.jukebox.dataloader;

import com.github.f4b6a3.tsid.TsidCreator;
import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import com.seonghyeon.jukebox.entity.SimilarSongEntity;
import com.seonghyeon.jukebox.entity.SongEntity;
import com.seonghyeon.jukebox.entity.SongMetricsEntity;
import io.r2dbc.spi.Statement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class SongBatchWriter {

    private final TransactionalOperator transactionalOperator;
    private final R2dbcEntityTemplate template;

    // [Songs Table]
    private static final String SONGS_TABLE = "songs";
    private static final String SONGS_COLUMNS = "(id, artist, title, album, release_date, release_year, genre, lyrics, length, emotion, total_likes)";
    private static final String SONGS_PLACEHOLDERS = makePlaceholders(11);

    // [Song Metrics Table]
    private static final String METRICS_TABLE = "song_metrics";
    private static final String METRICS_COLUMNS = "(song_id, musical_key, tempo, loudness_db, time_signature, explicit, popularity, energy, danceability, positiveness, speechiness, liveness, acousticness, instrumentalness, is_party, is_study, is_relaxation, is_exercise, is_running, is_yoga, is_driving, is_social, is_morning)";
    private static final String METRICS_PLACEHOLDERS = makePlaceholders(23);

    // [Similar Songs Table]
    private static final String SIMILAR_TABLE = "similar_songs";
    private static final String SIMILAR_COLUMNS = "(song_id, similar_artist, similar_title, similarity_score)";
    private static final String SIMILAR_PLACEHOLDERS = makePlaceholders(4);

    /**
     * 수집된 {@link SongDto} 리스트를 대용량 배치(Batch) 방식으로 데이터베이스에 영속화합니다.
     * * <p>성능 최적화를 위해 다음과 같은 전략을 사용합니다:
     * <ul>
     * <li><b>Multi-row Insert:</b> 각 테이블당 1,000건씩 묶어 단일 SQL 문으로 실행하여 네트워크 I/O 오버헤드를 최소화합니다.</li>
     * <li><b>Concurrency Control:</b> {@code flatMap}의 동시성 계수를 4로 설정하여 CPU 및 커넥션 자원을 효율적으로 분배합니다.</li>
     * <li><b>Transactional Integrity:</b> 부모(Songs)와 자식(Metrics, Similars) 엔티티 간의 원자성을 {@link TransactionalOperator}로 보장합니다.</li>
     * </ul>
     * * <p>이 메서드는 비동기 파이프라인으로 구성되어 있으나, 호출부(가상 스레드)에서의
     * 순차적 흐름 제어를 위해 마지막에 {@code .block()}을 수행합니다.</p>
     *
     * @param songDtoList 저장할 노래 데이터 리스트
     * @throws RuntimeException 데이터베이스 삽입 중 오류 발생 시 해당 chunk가 롤백됩니다.
     */
    public void flushAll(List<SongDto> songDtoList) {
        if (songDtoList == null) throw new IllegalArgumentException("songDtoList cannot be null");
        if (songDtoList.isEmpty()) return;

        Mono<Void> flushProcess = Flux.fromIterable(songDtoList)
                .map(dto -> new IdentifiedSong(TsidCreator.getTsid256().toLong(), dto))
                .buffer(1000)
                .flatMap(list -> insertAllSongs(list).then(Mono.defer(() -> insertChildEntities(list))), 4)
                .then();

        transactionalOperator.transactional(flushProcess).block();
    }

    record IdentifiedSong(Long id, SongDto dto) {
    }

    private Mono<Void> insertAllSongs(List<IdentifiedSong> batch) {
        if (batch.isEmpty()) return Mono.empty();
        String sql = buildBulkInsertSql(SONGS_TABLE, SONGS_COLUMNS, SONGS_PLACEHOLDERS, batch.size());

        return template.getDatabaseClient().inConnection(connection -> {
            Statement statement = connection.createStatement(sql);
            int idx = 0;
            for (IdentifiedSong identifiedSong : batch) {
                SongEntity s = SongEntity.fromDto(identifiedSong.dto());
                bindNext(statement, idx++, identifiedSong.id(), Long.class);
                bindNext(statement, idx++, s.artist(), String.class);
                bindNext(statement, idx++, s.title(), String.class);
                bindNext(statement, idx++, s.album(), String.class);
                bindNext(statement, idx++, s.releaseDate(), LocalDate.class);
                bindNext(statement, idx++, s.releaseYear(), Integer.class);
                bindNext(statement, idx++, s.genre(), String.class);
                bindNext(statement, idx++, s.lyrics(), String.class);
                bindNext(statement, idx++, s.length(), String.class);
                bindNext(statement, idx++, s.emotion(), String.class);
                bindNext(statement, idx++, (s.totalLikes() != null ? s.totalLikes() : 0L), Long.class);
            }
            return Flux.from(statement.execute()).then();
        });
    }

    private Mono<Void> insertChildEntities(List<IdentifiedSong> batch) {
        if (batch.isEmpty()) return Mono.empty();

        int size = batch.size();
        List<SongMetricsEntity> metricsList = new ArrayList<>(size);
        List<SimilarSongEntity> similarList = new ArrayList<>(size * 3);

        for (IdentifiedSong song : batch) {
            SongDto dto = song.dto();
            Long id = song.id();

            metricsList.add(SongMetricsEntity.fromDto(dto, id));

            if (dto.similarSongs() != null) {
                for (var similarDto : dto.similarSongs()) {
                    similarList.add(SimilarSongEntity.fromDto(similarDto, id));
                }
            }
        }
        return Mono.when(
                insertAllMetrics(metricsList),
                insertAllSimilars(similarList)
        ).then();
    }

    private Mono<Void> insertAllMetrics(List<SongMetricsEntity> metricsList) {
        if (metricsList.isEmpty()) return Mono.empty();
        String sql = buildBulkInsertSql(METRICS_TABLE, METRICS_COLUMNS, METRICS_PLACEHOLDERS, metricsList.size());

        return template.getDatabaseClient().inConnection(connection -> {
            Statement statement = connection.createStatement(sql);
            int idx = 0;

            for (SongMetricsEntity m : metricsList) {
                bindNext(statement, idx++, m.songId(), Long.class);
                bindNext(statement, idx++, m.musicalKey(), String.class);
                bindNext(statement, idx++, m.tempo(), Double.class);
                bindNext(statement, idx++, m.loudnessDb(), Double.class);
                bindNext(statement, idx++, m.timeSignature(), String.class);
                bindNext(statement, idx++, m.explicit(), String.class);
                bindNext(statement, idx++, m.popularity(), Integer.class);
                bindNext(statement, idx++, m.energy(), Integer.class);
                bindNext(statement, idx++, m.danceability(), Integer.class);
                bindNext(statement, idx++, m.positiveness(), Integer.class);
                bindNext(statement, idx++, m.speechiness(), Integer.class);
                bindNext(statement, idx++, m.liveness(), Integer.class);
                bindNext(statement, idx++, m.acousticness(), Integer.class);
                bindNext(statement, idx++, m.instrumentalness(), Integer.class);
                bindNext(statement, idx++, m.isParty(), Boolean.class);
                bindNext(statement, idx++, m.isStudy(), Boolean.class);
                bindNext(statement, idx++, m.isRelaxation(), Boolean.class);
                bindNext(statement, idx++, m.isExercise(), Boolean.class);
                bindNext(statement, idx++, m.isRunning(), Boolean.class);
                bindNext(statement, idx++, m.isYoga(), Boolean.class);
                bindNext(statement, idx++, m.isDriving(), Boolean.class);
                bindNext(statement, idx++, m.isSocial(), Boolean.class);
                bindNext(statement, idx++, m.isMorning(), Boolean.class);
            }

            return Flux.from(statement.execute()).then();
        });
    }

    private Mono<Void> insertAllSimilars(List<SimilarSongEntity> similarList) {
        if (similarList.isEmpty()) return Mono.empty();
        String sql = buildBulkInsertSql(SIMILAR_TABLE, SIMILAR_COLUMNS, SIMILAR_PLACEHOLDERS, similarList.size());

        return template.getDatabaseClient().inConnection(connection -> {
            Statement statement = connection.createStatement(sql);
            int idx = 0;
            for (SimilarSongEntity s : similarList) {
                bindNext(statement, idx++, s.songId(), Long.class);
                bindNext(statement, idx++, s.similarArtist(), String.class);
                bindNext(statement, idx++, s.similarTitle(), String.class);
                bindNext(statement, idx++, s.similarityScore(), Double.class);
            }
            return Flux.from(statement.execute()).then();
        });
    }

    // ---------- Helper Methods ----------

    private String buildBulkInsertSql(String table, String columns, String placeholders, int count) {
        String values = IntStream.range(0, count)
                .mapToObj(i -> placeholders)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " " + columns + " VALUES " + values;
    }

    private void bindNext(Statement statement, int index, Object value, Class<?> type) {
        if (value == null) {
            statement.bindNull(index, type);
        } else {
            statement.bind(index, value);
        }
    }

    private static String makePlaceholders(int parmCount) {
        return "(" + String.join(", ", Collections.nCopies(parmCount, "?")) + ")";
    }
}
