package com.seonghyeon.jukebox.dataloader;

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

    @Override
    public void run(ApplicationArguments args) {
        if (dataSetEnabled) {
            Path path = Path.of(dataSetLocation);
            Thread.ofVirtual().name("data-init-worker").start(() -> jsonBatchReader.read(path, list -> System.out.println(list.size()), 1000));
        } else {
            log.info("Dataset loading is disabled. (jukebox.dataset.enabled: false)");
        }
    }
}
