package com.seonghyeon.jukebox.repository.impl;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.repository.SongStatisticsCustomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Repository
@RequiredArgsConstructor
public class SongStatisticsCustomRepositoryImpl implements SongStatisticsCustomRepository {

    private final R2dbcEntityTemplate template;

    /// DB 에러 방지 및 컬럼 노출 제어를 위한 허용 정렬 필드 및 기본 정렬 설정
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("releaseYear", "artist", "albumCount");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("releaseYear"), Sort.Order.asc("artist"));

    @Override
    public Flux<SongStatisticsEntity> findAllByYearAndArtist(@Nullable Integer year, @Nullable String artist, Pageable pageable) {
        Pageable validatedPageable = validatePageable(pageable);

        Query query = Query.query(createCriteria(year, artist)).with(validatedPageable);
        return template.select(SongStatisticsEntity.class)
                .from("song_statistics")
                .matching(query)
                .all();
    }

    @Override
    public Mono<Long> countByYearAndArtist(@Nullable Integer year, @Nullable String artist) {
        return template.count(Query.query(createCriteria(year, artist)), SongStatisticsEntity.class);
    }

    private Pageable validatePageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        }
        boolean isValid = pageable.getSort().stream().allMatch(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()));
        return isValid ? pageable : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }

    private Criteria createCriteria(Integer year, String artist) {
        Criteria criteria = Criteria.empty();
        if (year != null) criteria = criteria.and("release_year").is(year);
        if (artist != null) criteria = criteria.and("artist").is(artist);
        return criteria;
    }
}
