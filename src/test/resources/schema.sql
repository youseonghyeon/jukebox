SET foreign_key_checks = 0;

drop table if exists songs;
create table songs
(
    id           bigint                              primary key,
    artist       varchar(1000)                       null,
    title        varchar(255)                        null,
    album        varchar(255)                        null,
    release_date date                                null,
    release_year int                                 null,
    genre        varchar(100)                        null,
    lyrics       mediumtext                          null,
    length       varchar(10)                         null,
    emotion      varchar(50)                         null,
    total_likes  bigint    default 0                 null,
    created_at   timestamp default CURRENT_TIMESTAMP null
);

create index idx_songs_year_artist
    on songs (release_year desc, artist(100) asc);

drop table if exists song_metrics;
create table if not exists song_metrics
(
    song_id          bigint       primary key,
    musical_key      varchar(255) null,
    tempo            double       null,
    loudness_db      double       null,
    time_signature   varchar(10)  null,
    explicit         varchar(50)  null,
    popularity       int          null,
    energy           int          null,
    danceability     int          null,
    positiveness     int          null,
    speechiness      int          null,
    liveness         int          null,
    acousticness     int          null,
    instrumentalness int          null,
    is_party         tinyint(1)   null,
    is_study         tinyint(1)   null,
    is_relaxation    tinyint(1)   null,
    is_exercise      tinyint(1)   null,
    is_running       tinyint(1)   null,
    is_yoga          tinyint(1)   null,
    is_driving       tinyint(1)   null,
    is_social        tinyint(1)   null,
    is_morning       tinyint(1)   null,

    constraint fk_song_metrics_song_id
        foreign key (song_id) references songs (id) on delete cascade
);

drop table if exists similar_songs;
create table similar_songs
(
    id               bigint auto_increment
        primary key,
    song_id          bigint        not null,
    similar_artist   varchar(1000) null,
    similar_title    varchar(255)  null,
    similarity_score double        null,
    constraint fk_similar_songs_song_id
        foreign key (song_id) references songs (id)
            on delete cascade
);

create index song_id
    on similar_songs (song_id);

SET foreign_key_checks = 1;
