package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongMetricsEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface SongMetricsRepository extends R2dbcRepository<SongMetricsEntity, Long> {
}
