package com.seonghyeon.jukebox.dataloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBatchReaderTest {

    private JsonBatchReader jsonBatchReader;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonBatchReader = new JsonBatchReader(objectMapper);
    }

    // 테스트용 DTO
    record TestData(int id, String name) {
    }

    @Test
    @DisplayName("데이터가 배치 사이즈보다 많을 때, 나누어서 콜백이 실행")
    void process_batches_correctly() throws IOException {
        // given
        // 10개의 데이터 생성
        List<TestData> allData = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            allData.add(new TestData(i, "name" + i));
        }
        Path jsonFile = createJsonFile("test_data_10.json", allData);

        // 검증을 위해 처리된 데이터를 모을 리스트
        List<List<TestData>> capturedBatches = new ArrayList<>();

        // when
        // 배치 사이즈 3으로 설정 (예상: 3, 3, 3, 1 -> 총 4번 호출)
        jsonBatchReader.process(jsonFile, batch -> {
            // 주의: 원본 코드에서 chunk.clear()를 하므로, 테스트에선 복사해서 저장해야 함
            capturedBatches.add(new ArrayList<>(batch));
        }, 3, TestData.class, 0);

        // then
        assertThat(capturedBatches).hasSize(4); // 4번 호출됨
        assertThat(capturedBatches.get(0)).hasSize(3); // 첫 번째 배치
        assertThat(capturedBatches.get(1)).hasSize(3); // 두 번째 배치
        assertThat(capturedBatches.get(2)).hasSize(3); // 세 번째 배치
        assertThat(capturedBatches.get(3)).hasSize(1); // 마지막 배치

        // 데이터 순서 확인
        assertThat(capturedBatches.get(0).get(0).id()).isEqualTo(1);
        assertThat(capturedBatches.get(0).get(1).id()).isEqualTo(2);
        assertThat(capturedBatches.get(0).get(2).id()).isEqualTo(3);
        assertThat(capturedBatches.get(1).get(0).id()).isEqualTo(4);
        assertThat(capturedBatches.get(1).get(1).id()).isEqualTo(5);
        assertThat(capturedBatches.get(1).get(2).id()).isEqualTo(6);
        // 최종 데이터
        assertThat(capturedBatches.get(3).get(0).id()).isEqualTo(10);
    }

    @Test
    @DisplayName("skipCount가 설정되면 앞부분 데이터를 건너 뜀")
    void process_with_skip_count() throws IOException {
        // given
        List<TestData> allData = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            allData.add(new TestData(i, "name" + i));
        }
        Path jsonFile = createJsonFile("test_data_skip.json", allData);

        List<TestData> result = new ArrayList<>();

        // when
        // 총 5개 중 앞의 2개를 건너뛰고, 배치 10으로 실행
        jsonBatchReader.process(jsonFile, result::addAll, 10, TestData.class, 2);

        // then
        assertThat(result).hasSize(3); // 5 - 2 = 3개
        assertThat(result.get(0).id()).isEqualTo(3); // ID 1, 2는 건너뛰고 3부터 시작
        assertThat(result.get(2).id()).isEqualTo(5);
    }

    @Test
    @DisplayName("빈 JSON 배열 파일은 에러 없이 아무 작업도 수행되지 않음")
    void process_empty_array() throws IOException {
        // given
        Path jsonFile = tempDir.resolve("empty.json");
        Files.writeString(jsonFile, "[]"); // 빈 배열

        List<List<TestData>> captured = new ArrayList<>();

        // when
        jsonBatchReader.process(jsonFile, captured::add, 10, TestData.class, 0);

        // then
        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("파일이 존재하지 않으면 IllegalArgumentException 발생")
    void throw_exception_when_file_not_found() {
        Path nonExistentPath = tempDir.resolve("ghost.json");

        assertThatThrownBy(() ->
                jsonBatchReader.process(nonExistentPath, list -> {
                }, 10, TestData.class, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    @DisplayName("배치 사이즈가 0 이하이면 예외 발생")
    void throw_exception_when_invalid_batch_size() throws IOException {
        Path jsonFile = createJsonFile("dummy.json", List.of(new TestData(1, "a")));

        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, list -> {
                }, 0, TestData.class, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch size must be greater than zero");
    }

    @Test
    @DisplayName("실제 JSON 파일(5건)을 SongDto로 매핑하여 배치 처리")
    void process_real_data_with_song_dto() throws IOException {
        // 실제 데이터 기반 테스트
        // given
        Path jsonFile = createRealDataFile("real_songs.json");
        List<List<SongDto>> capturedBatches = new ArrayList<>();

        // when
        // 배치 사이즈 2로 설정 -> [2건, 2건, 1건] 총 3번 호출 예상
        jsonBatchReader.process(jsonFile, batch -> {
            capturedBatches.add(new ArrayList<>(batch));
        }, 2, SongDto.class, 0);

        // then
        // 1. 배치 횟수 검증
        assertThat(capturedBatches).hasSize(3);
        assertThat(capturedBatches.get(0)).hasSize(2);
        assertThat(capturedBatches.get(2)).hasSize(1);

        // 2. 데이터 매핑 검증 (첫 번째 곡: "Even When the Waters Cold")
        SongDto firstSong = capturedBatches.get(0).get(0);

        // JSON의 "Artist(s)": "!!!" -> DTO의 artist()
        assertThat(firstSong.artists()).isEqualTo("!!!");

        // JSON의 "song": "Even When the Waters Cold" -> DTO의 title()
        assertThat(firstSong.song()).isEqualTo("Even When the Waters Cold");

        // JSON의 "Release Date": "2013-04-29" -> DTO의 releaseDate()
        assertThat(firstSong.releaseDate().toString()).isEqualTo("2013-04-29");

        // 3. 중첩 리스트(Similar Songs) 검증
        assertThat(firstSong.similarSongs()).hasSize(3);
        assertThat(firstSong.similarSongs().get(0).artist()).isEqualTo("Corey Smith");
    }


    @Test
    @DisplayName("JSON 형식이 잘못된(깨진) 파일은 RuntimeException이 발생")
    void throw_exception_when_json_is_malformed() throws IOException {
        // given: 닫는 괄호가 없는 깨진 JSON
        Path brokenFile = tempDir.resolve("broken.json");
        Files.writeString(brokenFile, "[ {\"id\": 1, \"name\": \"broken\" ");

        // when & then
        assertThatThrownBy(() ->
                jsonBatchReader.process(brokenFile, list -> {
                }, 10, TestData.class, 0)
        )
                .isInstanceOf(RuntimeException.class) // JsonBatchReader가 IOException을 RuntimeException으로 감싸서 던짐
                .hasCauseInstanceOf(IOException.class); // 원인은 Jackson Parsing Error
    }

    @Test
    @DisplayName("skipCount가 전체 데이터 개수보다 크면, 에러 없이 처리된 데이터가 0건")
    void process_when_skip_count_exceeds_data_size() throws IOException {
        // given: 데이터 5개
        List<TestData> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) data.add(new TestData(i, "a"));
        Path jsonFile = createJsonFile("overflow.json", data);

        List<List<TestData>> captured = new ArrayList<>();

        // when: 100개를 건너뛰라고 요청 (5개밖에 없는데)
        jsonBatchReader.process(jsonFile, captured::add, 10, TestData.class, 100);

        // then: 에러는 안 나고, 처리된 배치는 없어야 함
        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("내용이 없는 0 byte 파일은 빈 배열과 동일하게 처리되거나 예외를 던지지 않음")
    void process_zero_byte_file() throws IOException {
        // given: 0 byte 파일 생성
        Path emptyFile = tempDir.resolve("zero_byte.json");
        Files.createFile(emptyFile);

        List<List<TestData>> captured = new ArrayList<>();

        // when
        // Jackson 버전에 따라 0byte는 파싱 에러가 날 수도 있고, null로 끝날 수도 있음.
        jsonBatchReader.process(emptyFile, captured::add, 10, TestData.class, 0);

        // then
        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("콜백(DB저장) 처리 중 예외가 발생하면, 프로세스가 중단되고 예외를 던져야 함")
    void stop_process_when_callback_throws_exception() throws IOException {
        // given
        List<TestData> data = List.of(new TestData(1, "a"), new TestData(2, "b"));
        Path jsonFile = createJsonFile("callback_error.json", data);

        // when & then
        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, batch -> {
                    throw new RuntimeException("DB Connection Fail");
                }, 1, TestData.class, 0)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB Connection Fail");
    }

    @Test
    @DisplayName("데이터 개수가 배치 사이즈의 배수일 때, 빈 배치가 추가로 호출되지 않아야 함")
    void process_exact_multiple_batch_size() throws IOException {
        // given: 데이터 9개
        List<TestData> allData = new ArrayList<>();
        for (int i = 1; i <= 9; i++) allData.add(new TestData(i, "data"));
        Path jsonFile = createJsonFile("exact_batch.json", allData);

        List<List<TestData>> capturedBatches = new ArrayList<>();

        // when: 배치 사이즈 3 (예상: 3, 3, 3 -> 총 3번)
        jsonBatchReader.process(jsonFile, batch -> {
            capturedBatches.add(new ArrayList<>(batch));
        }, 3, TestData.class, 0);

        // then
        assertThat(capturedBatches).hasSize(3); // 4번이 아니라 딱 3번이어야 함
        assertThat(capturedBatches.get(0)).hasSize(3);
        assertThat(capturedBatches.get(2)).hasSize(3);

        // 마지막 배치가 비어있지 않은지 확인
        assertThat(capturedBatches.get(2)).isNotEmpty();
    }

    @Test
    @DisplayName("파라미터가 null이면 IllegalArgumentException 발생")
    void throw_exception_when_parameters_are_null() throws IOException {
        // given
        Path jsonFile = createJsonFile("null_params.json", List.of(new TestData(1, "a")));

        // when & then
        assertThatThrownBy(() ->
                jsonBatchReader.process(null, list -> {
                }, 10, TestData.class, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path must not be null");

        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, null, 10, TestData.class, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Callback must not be null");

        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, list -> {
                }, 10, null, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target type must not be null");
    }

    @Test
    @DisplayName("skipCount가 음수이면 IllegalArgumentException 발생")
    void throw_exception_when_skip_count_is_negative() throws IOException {
        // given
        Path jsonFile = createJsonFile("negative_skip.json", List.of(new TestData(1, "a")));

        // when & then
        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, list -> {
                }, 10, TestData.class, -5)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skip count cannot be negative");
    }

    @Test
    @DisplayName("배치 사이즈가 0이면 IllegalArgumentException 발생")
    void throw_exception_when_batch_size_is_zero() throws IOException {
        // given
        Path jsonFile = createJsonFile("zero_batch_size.json", List.of(new TestData(1, "a")));

        // when & then
        assertThatThrownBy(() ->
                jsonBatchReader.process(jsonFile, list -> {
                }, 0, TestData.class, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch size must be greater than zero");
    }

    // --- Helper Method ---
    private Path createJsonFile(String fileName, List<TestData> data) throws IOException {
        Path path = tempDir.resolve(fileName);
        // ObjectMapper로 JSON 파일 생성
        objectMapper.writeValue(path.toFile(), data);
        return path;
    }

    private Path createRealDataFile(String fileName) throws IOException { // 실 데이터 발췌
        Path path = tempDir.resolve(fileName);
        // ObjectMapper로 실제 JSON 파일 생성
        String json = """
                {"Artist(s)":"!!!","song":"Even When the Waters Cold","text":"Friends told her she was better off at the bottom of a river Than in a bed with him He said \\"Until you try both, you won't know what you like better Why don't we go for a swim?\\" Well, friends told her this and friends told her that But friends don't choose what echoes in your head When she got bored with all the idle chit-and-chat Kept thinking 'bout what he said I'll swim even when the water's cold That's the one thing that I know Even when the water's cold She remembers it fondly, she doesn't remember it all But what she does, she sees clearly She lost his number, and he never called But all she really lost was an earring The other's in a box with others she has lost I wonder if she still hears me I'll swim even when the water's cold That's the one thing that I know Even when the water's cold If you believe in love You know that sometimes it isn't Do you believe in love? Then save the bullshit questions Sometimes it is and sometimes it isn't Sometimes it's just how the light hits their eyes Do you believe in love?","Length":"03:47","emotion":"sadness","Genre":"hip hop","Album":"Thr!!!er","Release Date":"2013-04-29","Key":"D min","Tempo":0.4378698225,"Loudness (db)":0.785065407,"Time signature":"4\\/4","Explicit":"No","Popularity":"40","Energy":"83","Danceability":"71","Positiveness":"87","Speechiness":"4","Liveness":"16","Acousticness":"11","Instrumentalness":"0","Good for Party":0,"Good for Work\\/Study":0,"Good for Relaxation\\/Meditation":0,"Good for Exercise":0,"Good for Running":0,"Good for Yoga\\/Stretching":0,"Good for Driving":0,"Good for Social Gatherings":0,"Good for Morning Routine":0,"Similar Songs":[{"Similar Artist 1":"Corey Smith","Similar Song 1":"If I Could Do It Again","Similarity Score":0.9860607848},{"Similar Artist 2":"Toby Keith","Similar Song 2":"Drinks After Work","Similarity Score":0.9837194774},{"Similar Artist 3":"Space","Similar Song 3":"Neighbourhood","Similarity Score":0.9832363508}]}
                {"Artist(s)":"!!!","song":"One Girl \\/ One Boy","text":"Well I heard it, playing soft From a drunken bar's jukebox And for a moment I was lost In remembering, what I never forgot And I never felt guilt about The trouble that we got into I just couldn't let that honey hide inside of you And just because now it's different Doesn't change what it meant And when I hear that song You know I'm only gonna Think about one girl I think about you When I sing this song You know I'm only gonna Sing it for one girl Ooh ooh ooh Well I heard it, playing loud I never knew what it was about Till I fell silent in a crowd I just turned around and walked straight out 'Cause I guess I felt guilty About the trouble that I caused you Putting myself first, just like I always do But that doesn't change how I feel 'Cause when I hear it, it feels real And when I hear that song You know I'm only gonna Think about one boy I think about you And when I sing this song You know I'm only gonna Sing it for one boy Ooh ooh ooh Well, I can't forget Things you said or your kisses And I keep your secrets Where I keep your promises But you need my confessions About as much as you need my lies And I guess it took that song to make me realize [Instrumental Break]","Length":"04:03","emotion":"sadness","Genre":"hip hop","Album":"Thr!!!er","Release Date":"2013-04-29","Key":"A# min","Tempo":0.5088757396,"Loudness (db)":0.8050508721,"Time signature":"4\\/4","Explicit":"No","Popularity":"42","Energy":"85","Danceability":"70","Positiveness":"87","Speechiness":"4","Liveness":"32","Acousticness":"0","Instrumentalness":"0","Good for Party":0,"Good for Work\\/Study":0,"Good for Relaxation\\/Meditation":0,"Good for Exercise":0,"Good for Running":0,"Good for Yoga\\/Stretching":0,"Good for Driving":0,"Good for Social Gatherings":0,"Good for Morning Routine":0,"Similar Songs":[{"Similar Artist 1":"Hiroyuki Sawano","Similar Song 1":"BRE@TH\\/\\/LESS","Similarity Score":0.9954090051},{"Similar Artist 2":"When In Rome","Similar Song 2":"Heaven Knows","Similarity Score":0.9909052447},{"Similar Artist 3":"Justice Crew","Similar Song 3":"Everybody","Similarity Score":0.9844825577}]}
                {"Artist(s)":"!!!","song":"Pardon My Freedom","text":"Oh my god, did I just say that out loud? Should've known this was the kind of place That that sort of thing just wasn't allowed Should've known by the color of the drapes (Oh, my bad, venetian blinds) What the hell was I thinking saying exactly what's on my mind? But I won't deny I got a dirty mouth My mother tried, my father tried, my teachers tried But they couldn't wash it out And look at me now up here running my mouth I just open it up and see what comes running out Like I give a fuck, like I give a shit Like I give a fuck about that shit Like I give a fuck about that motherfucking shit And you can tell the president to suck my fucking dick Does that sound intelligent? Like I give a fucking frick Tell the FBI put me on the list because Lennon wasn't this dangerous Call the Christians, tell them all that I'm taller than Jesus They tore down the parking lot and put in a parking lot and what do they got? And if you followed the plot then you know I'm not going to give it a second thought Yeah, let those pigs play because they'll all fucking pay Yeah, karma's a fact, that shit'll come back someday And I'll be like Like I give a fuck, like I give a shit like I give a shit about that fuck Like I give a fuck about that motherfucking shit","Length":"05:51","emotion":"joy","Genre":"hip hop","Album":"Louden Up Now","Release Date":"2004-06-08","Key":"A Maj","Tempo":0.5325443787,"Loudness (db)":0.7994186047,"Time signature":"4\\/4","Explicit":"No","Popularity":"29","Energy":"89","Danceability":"71","Positiveness":"63","Speechiness":"8","Liveness":"64","Acousticness":"0","Instrumentalness":"20","Good for Party":0,"Good for Work\\/Study":0,"Good for Relaxation\\/Meditation":0,"Good for Exercise":1,"Good for Running":0,"Good for Yoga\\/Stretching":0,"Good for Driving":0,"Good for Social Gatherings":0,"Good for Morning Routine":0,"Similar Songs":[{"Similar Artist 1":"Ricky Dillard","Similar Song 1":"More Abundantly Medley Live","Similarity Score":0.9931760848},{"Similar Artist 2":"Juliet","Similar Song 2":"Avalon","Similarity Score":0.9651469455},{"Similar Artist 3":"The Jacksons","Similar Song 3":"Lovely One","Similarity Score":0.9567517825}]}
                {"Artist(s)":"!!!","song":"Ooo","text":"[Verse 1] Remember when I called you on the telephone? You were so far away It was raining in New York, did I forget to say? It was later than I wanted it to be On an early summer's night The kind where you can't help but feel alive and free And I told you \\"From here on out, it's just you and me\\" [Chorus] Shouldn't we? Should we be? Should we be together for like ever, girl? Shouldn't we? Should we be? Should we be together for like ever, girl? [Verse 2] We drove across the bridge and I knew that we'd be okay On an early summer's day No clouds up in the sky, did I forget to say? You'd been up all night before I'd barely slept at all It's the kind of thing that's sure to make you feel so small And I asked you \\"Do you remember that phone call?\\" [Chorus] Shouldn't we? Should we be? Should we be together for like ever, girl? Shouldn't we? Should we be? Should we be together for like ever, girl? [Bridge] Do you think we should be together? Forever and ever and ever and ever and ever Ever, ever, ever, ever Ever, ever, ever, ever, ever [Chorus] Should we be? Shouldn't we? Should we be? Should we be together for like ever, girl? (Should we be together?) (Should we be?) Shouldn't we? Should we be? Should we be together for like ever, girl? (Should we be together?) (Forever and ever) [Outro] Don't you think we should be together? (Should we be together?) Forever and ever and ever, ever and ever (Forever and ever) Ever, ever Don't you think?","Length":"03:44","emotion":"joy","Genre":"hip hop","Album":"As If","Release Date":"2015-10-16","Key":"A min","Tempo":0.5384615385,"Loudness (db)":0.8110465116,"Time signature":"4\\/4","Explicit":"No","Popularity":"24","Energy":"84","Danceability":"78","Positiveness":"97","Speechiness":"4","Liveness":"12","Acousticness":"12","Instrumentalness":"0","Good for Party":0,"Good for Work\\/Study":0,"Good for Relaxation\\/Meditation":0,"Good for Exercise":1,"Good for Running":0,"Good for Yoga\\/Stretching":0,"Good for Driving":0,"Good for Social Gatherings":0,"Good for Morning Routine":0,"Similar Songs":[{"Similar Artist 1":"Eric Clapton","Similar Song 1":"Man Overboard","Similarity Score":0.9927492491},{"Similar Artist 2":"Roxette","Similar Song 2":"Don't Believe In Accidents","Similarity Score":0.9914939607},{"Similar Artist 3":"Tiwa Savage","Similar Song 3":"My Darlin","Similarity Score":0.9903805989}]}
                {"Artist(s)":"!!!","song":"Freedom 15","text":"[Verse 1] Calling me like I got something to say You thought wrong, but you do it anyway How's it been? Oh, not much, same for me, please go away I can put it on if that's what you want You'd like to get together, but I'd rather not Calls to mind a simpler time that who gave a shit forgot [Pre-Chorus] Used to have something to prove Now it's something to hide And everyone assumes It's probably best not to pry And anyway, you're probably fine Now that you got what you wanted Now that you've got your freedom Now that you've got your freedom [Chorus] Your freedom How's that working for you, baby? Your freedom How's that working for you, baby? Your freedom How's that working for you? [Verse 2] Who was it then? Who was it that I knew? Was it you who I knew then? Or is this the real you? No offense, but whoever this person is I don't have much interest in [Pre-Chorus] Remembered more by some ill-advised tattoo Used to have a couple, now you've got a few If I know you, and I think I do You forgot it by the time the ink was blue And it won't be true 'til you've found your freedom [Chorus] Your freedom How's that working for you, baby? Your freedom How's that working for you, baby? Your freedom How's that working for you? How's that working for you, baby? How's that working for you, child? [Verse 3] Used to have something to prove Now you got something to hide And everyone assumes It's probably best not to pry [Bridge] Well, how's that working for you, baby? How is that working for you, child? Well, how's that working for you, baby? How is that working for you? Well, how's that working for you, babe? How is that working for you, child? How's that working for you, baby? How is that working for you, child? Child Child (Freedom, baby) Child Child [Outro] Did you figure it out? Did you figure it out? Did you figure it out? Did you figure it out, who the song is about? Did you figure it out? Did you figure it out? Did you figure it out? Did you figure it out, who the song is about? How's it working for you, baby? How's it working for you? How's it working for you, baby? How's it working for, working for you? Did you figure it out? Did you figure it out? How's it working for you, baby? How's it working for you? How's it working for you, baby? How's it working for, working for you? Did you figure it out? Did you figure it out? How's it working for you? How's it working for you? How's it working for you? Don't figure it out, don't figure it out How's it working for you? How's it working for you? How's it working for you? Did you figure it out? Did you figure it out? Did you figure it out? Did you figure it out? Don't figure it out, don't figure it out","Length":"06:00","emotion":"joy","Genre":"hip hop","Album":"As If","Release Date":"2015-10-16","Key":"F min","Tempo":0.5443786982,"Loudness (db)":0.8083212209,"Time signature":"4\\/4","Explicit":"No","Popularity":"30","Energy":"71","Danceability":"77","Positiveness":"70","Speechiness":"7","Liveness":"10","Acousticness":"4","Instrumentalness":"1","Good for Party":0,"Good for Work\\/Study":0,"Good for Relaxation\\/Meditation":0,"Good for Exercise":1,"Good for Running":0,"Good for Yoga\\/Stretching":0,"Good for Driving":0,"Good for Social Gatherings":0,"Good for Morning Routine":0,"Similar Songs":[{"Similar Artist 1":"Cibo Matto","Similar Song 1":"Lint Of Love","Similarity Score":0.9816095588},{"Similar Artist 2":"Barrington Levy","Similar Song 2":"Better Than Gold","Similarity Score":0.9815243377},{"Similar Artist 3":"Freestyle","Similar Song 3":"Its Automatic","Similarity Score":0.9814147734}]}
                """;
        Files.writeString(path, json);
        return path;
    }
}
