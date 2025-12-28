package com.seonghyeon.jukebox.dataloader;

import com.github.f4b6a3.tsid.TsidCreator;
import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import com.seonghyeon.jukebox.entity.SimilarSongEntity;
import com.seonghyeon.jukebox.entity.SongEntity;
import com.seonghyeon.jukebox.entity.SongMetricsEntity;
import com.seonghyeon.jukebox.repository.SongRepository;
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

    public void flushAll(List<SongDto> songDtoList) {
        Mono<Void> flushProcess = Flux.fromIterable(songDtoList)
                .flatMap(dto -> Mono.just(new WithId(TsidCreator.getTsid256().toLong(), dto)))
                .buffer(1000)
                .flatMap(list -> insertAll(list).then(Mono.defer(() -> saveChildEntities(list))), 30)
                .then();
        transactionalOperator.transactional(flushProcess).block(); // 동기화
    }

    record WithId(Long songId, SongDto songDto) {
    }

    private Mono<Void> insertAll(List<WithId> songList) {
        String placeholders = "(" + String.join(", ", Collections.nCopies(11, "?")) + ")";

        // 2. 전체 VALUES 절 생성: (?, ...), (?, ...)
        String valueClause = IntStream.range(0, songList.size())
                .mapToObj(i -> placeholders)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO songs " +
                     "(id, artist, title, album, release_date, release_year, genre, lyrics, length, emotion, total_likes) " +
                     "VALUES " + valueClause;

        return template.getDatabaseClient().inConnection(connection -> {
            Statement statement = connection.createStatement(sql);
            int idx = 0;
            for (WithId song : songList) {
                SongEntity songe = SongEntity.fromDto(song.songDto());
                bindNext(statement, idx++, song.songId(), Long.class);
                bindNext(statement, idx++, songe.artist(), String.class);
                bindNext(statement, idx++, songe.title(), String.class);
                bindNext(statement, idx++, songe.album(), String.class);
                bindNext(statement, idx++, songe.releaseDate(), LocalDate.class);
                bindNext(statement, idx++, songe.releaseYear(), Integer.class);
                bindNext(statement, idx++, songe.genre(), String.class);
                bindNext(statement, idx++, songe.lyrics(), String.class);
                bindNext(statement, idx++, songe.length(), String.class);
                bindNext(statement, idx++, songe.emotion(), String.class);
                bindNext(statement, idx++, (songe.totalLikes() != null ? songe.totalLikes() : 0L), Long.class);
            }
            statement.returnGeneratedValues("id");
            return Flux.from(statement.execute()).then();
        });
    }

    private Mono<Void> saveChildEntities(List<WithId> pairs) {
        int size = pairs.size();
        List<SongMetricsEntity> metricsList = new ArrayList<>(size);
        List<SimilarSongEntity> similarList = new ArrayList<>(size * 3);

        for (WithId pair : pairs) {
            SongDto songDto = pair.songDto();
            Long songId = pair.songId();

            metricsList.add(SongMetricsEntity.fromDto(songDto, songId));
            if (songDto.similarSongs() != null) {
                songDto.similarSongs().forEach(similarDto ->
                        similarList.add(SimilarSongEntity.fromDto(similarDto, songId))
                );
            }
        }

        return Mono.when(
                batchInsertMetrics(metricsList),
                batchInsertSimilars(similarList)
        ).then();
    }

    // =================================================================================
    // 1. Song Metrics Batch Insert (Multi-row)
    // =================================================================================
    private Mono<Void> batchInsertMetrics(List<SongMetricsEntity> metricsList) {
        if (metricsList.isEmpty()) return Mono.empty();

        // 23개의 파라미터 홀더 (?, ?, ... ) 생성
        String placeholders = "(" + String.join(", ", Collections.nCopies(23, "?")) + ")";

        // 전체 VALUES 절 생성: (?, ...), (?, ...)
        String valueClause = IntStream.range(0, metricsList.size())
                .mapToObj(i -> placeholders)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO song_metrics " +
                     "(song_id, musical_key, tempo, loudness_db, time_signature, explicit, popularity, energy, danceability, positiveness, speechiness, liveness, acousticness, instrumentalness, is_party, is_study, is_relaxation, is_exercise, is_running, is_yoga, is_driving, is_social, is_morning) " +
                     "VALUES " + valueClause;

        return template.getDatabaseClient().inConnection(connection -> {
            Statement statement = connection.createStatement(sql);
            int idx = 0; // 전체 파라미터 인덱스

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

                // Boolean 필드 (MySQL 등에서는 0/1로 변환되거나 드라이버가 처리)
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

    // =================================================================================
    // 2. Similar Songs Batch Insert (Multi-row)
    // =================================================================================
    private Mono<Void> batchInsertSimilars(List<SimilarSongEntity> similarList) {
        if (similarList.isEmpty()) return Mono.empty();

        // 4개의 파라미터 홀더 (?, ?, ?, ?)
        String placeholders = "(?, ?, ?, ?)";

        String valueClause = IntStream.range(0, similarList.size())
                .mapToObj(i -> placeholders)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO similar_songs " +
                     "(song_id, similar_artist, similar_title, similarity_score) " +
                     "VALUES " + valueClause;

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

    private void bindNext(Statement statement, int index, Object value, Class<?> type) {
        if (value == null) {
            statement.bindNull(index, type);
        } else {
            statement.bind(index, value);
        }
    }
}
