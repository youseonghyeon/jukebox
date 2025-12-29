package com.seonghyeon.jukebox.dataloader;

import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class SongDataInitializer implements ApplicationRunner {

    @Value("${jukebox.dataset.enabled}")
    private boolean dataSetEnabled;

    @Value("${jukebox.dataset.location}")
    private String dataSetLocation;

    private final JsonBatchReader jsonBatchReader;
    private final SongBatchWriter songBatchWriter;

    @Override
    public void run(ApplicationArguments args) {
        if (dataSetEnabled) {
            Path path = Path.of(dataSetLocation);
            Thread.ofVirtual().name("data-init-worker").start(() -> jsonBatchReader.process(path, songBatchWriter::flushAll, 1000, SongDto.class, 0));
        } else {
            log.info("Dataset loading is disabled. (jukebox.dataset.enabled: false)");
        }
    }
}
