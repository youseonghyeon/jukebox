package com.seonghyeon.jukebox;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> mysql;

    static {
        // 클래스 로드 시점에 컨테이너를 딱 한 번 생성 및 시작
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("schema.sql")
                .withUrlParam("ssl", "false")
                .withUrlParam("allowPublicKeyRetrieval", "true")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--default-authentication-plugin=mysql_native_password"
                );

        mysql.start(); // 수동 시작
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 런타임에 결정된 포트와 호스트 정보를 R2DBC 설정에 주입
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:mysql://%s:%d/%s?ssl=false",
                        mysql.getHost(), mysql.getFirstMappedPort(), mysql.getDatabaseName()));
        registry.add("spring.r2dbc.username", mysql::getUsername);
        registry.add("spring.r2dbc.password", mysql::getPassword);
    }
}
