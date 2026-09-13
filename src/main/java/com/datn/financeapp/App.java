package com.datn.financeapp;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class App {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        configureFlywayLocationsIfPresent();
        SpringApplication.run(App.class, args);
    }

    private static void configureFlywayLocationsIfPresent() {
        if (System.getProperty("spring.flyway.locations") == null && System.getenv("SPRING_FLYWAY_LOCATIONS") == null) {
            if (new File("db/migration").isDirectory()) {
                System.setProperty("spring.flyway.locations", "filesystem:db/migration");
            } else if (new File("../../db/migration").isDirectory()) {
                System.setProperty("spring.flyway.locations", "filesystem:../../db/migration");
            }
        }
    }

    private static void loadDotenvIfPresent() {
        String[] candidatePaths = {
                ".env",
                "source/finance-ai-server/.env",
                "source/server/.env",
                "../source/finance-ai-server/.env"
        };
        for (String candidate : candidatePaths) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            File file = path.toFile();
            if (file.isFile() && file.exists()) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                } catch (Exception ignored) {
                }
                break;
            }
        }
    }
}

