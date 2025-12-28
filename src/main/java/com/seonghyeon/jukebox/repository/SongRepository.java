package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface SongRepository extends R2dbcRepository<SongEntity, Long> {
}
