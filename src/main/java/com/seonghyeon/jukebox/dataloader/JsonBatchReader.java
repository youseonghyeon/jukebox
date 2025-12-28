package com.seonghyeon.jukebox.dataloader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonBatchReader {

    private final ObjectMapper objectMapper;

    public void read(Path path, Consumer<List<SongDto>> callback, int batchSize) {
        long startMillis = System.currentTimeMillis();
        long processCount = 0;
        if (!Files.exists(path)) throw new IllegalArgumentException("File not found. path: " + path);
        if (batchSize <= 0) throw new IllegalArgumentException("Batch size must be greater than zero.");

        try (InputStream is = Files.newInputStream(path);
             JsonParser parser = objectMapper.createParser(is);
             MappingIterator<SongDto> it = objectMapper.readValues(parser, SongDto.class)
        ) {
            if (parser.nextToken() == JsonToken.START_ARRAY) {
                parser.nextToken();
                if (parser.currentToken() == JsonToken.END_ARRAY) {
                    return;
                }
            }

            List<SongDto> chunk = new ArrayList<>(batchSize);
            while (it.hasNext()) {
                SongDto next = it.next();
                chunk.add(next);

                if (chunk.size() >= batchSize) {
                    callback.accept(chunk);
                    processCount += chunk.size();
                    log.info("Processing batch chunk. processedCount={}", processCount);
                    chunk.clear(); // callback 동기 호출 (메모리 재사용)
//                    chunk = new ArrayList<>(batchSize); // callback 비동기 호출
                }
            }
            // 잔여 데이터 처리
            if (!chunk.isEmpty()) {
                callback.accept(chunk);
                processCount += chunk.size();
                log.info("Processing batch chunk. processedCount={}", processCount);
            }
        } catch (IOException e) {
            log.error("Error reading JSON file and, process callback", e);
            throw new RuntimeException(e);
        }
        long endMillis = System.currentTimeMillis();
        log.info("JSON batch processing completed in {} ms", (endMillis - startMillis));
    }
}
