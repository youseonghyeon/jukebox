package com.seonghyeon.jukebox.dataloader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public <E> void process(Path path, Consumer<List<E>> callback, int batchSize, Class<E> targetType, long skipCount) {
        long startMillis = System.currentTimeMillis();
        long processCount = 0;
        if (!Files.exists(path)) throw new IllegalArgumentException("File not found. path: " + path);
        if (batchSize <= 0) throw new IllegalArgumentException("Batch size must be greater than zero.");
        if (skipCount < 0) throw new IllegalArgumentException("Skip count cannot be negative.");

        try (InputStream is = Files.newInputStream(path);
             JsonParser parser = objectMapper.createParser(is);
             MappingIterator<E> it = objectMapper.readValues(parser, targetType)
        ) {
            if (parser.nextToken() == JsonToken.START_ARRAY) {
                parser.nextToken();
                if (parser.currentToken() == JsonToken.END_ARRAY) {
                    return;
                }
            }

            long skipped = 0;
            while (it.hasNext() && skipped < skipCount) {
                it.next();
                skipped++;
            }
            if (skipped > 0) {
                log.info("기존 처리 내역 {}건을 건너뛰었습니다. (Resume)", skipped);
            }

            List<E> chunk = new ArrayList<>(batchSize);
            while (it.hasNext()) {
                E next = it.next();
                chunk.add(next);

                if (chunk.size() >= batchSize) {
                    callback.accept(chunk);
                    processCount += chunk.size();
                    log.info("처리된 데이터 건수: {}", processCount);
                    chunk.clear(); // callback 동기 호출 (메모리 재사용)
//                    chunk = new ArrayList<>(batchSize); // callback 비동기 호출
                }
            }
            // 잔여 데이터 처리
            if (!chunk.isEmpty()) {
                callback.accept(chunk);
                processCount += chunk.size();
                log.info("처리된 데이터 건수: {}", processCount);
            }
        } catch (IOException e) {
            log.error("Error reading JSON file and, process callback", e);
            throw new RuntimeException(e);
        }
        long endMillis = System.currentTimeMillis();
        log.info("JSON batch reading completed in {} ms", (endMillis - startMillis));
    }
}
