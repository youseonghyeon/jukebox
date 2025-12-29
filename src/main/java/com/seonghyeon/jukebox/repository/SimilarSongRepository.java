package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SimilarSongEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface SimilarSongRepository extends R2dbcRepository<SimilarSongEntity, Long> {

    Flux<SimilarSongEntity> findAllBySongId(Long songId);


}
