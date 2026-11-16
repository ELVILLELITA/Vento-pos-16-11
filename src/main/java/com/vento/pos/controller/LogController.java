package com.vento.pos.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {
    private static final int MAX_LINES = 150;
    private final Path logFile;

    public LogController(@Value("${app.log.file:logs/spring.log}") String logFile) {
        this.logFile = Path.of(logFile).toAbsolutePath().normalize();
    }

    @GetMapping(value = "/errores", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> errores() {
        if (!Files.isRegularFile(logFile)) return List.of();
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            return lines.size() <= MAX_LINES
                    ? lines
                    : lines.subList(lines.size() - MAX_LINES, lines.size());
        } catch (IOException exception) {
            return Collections.singletonList("No se pudo leer el archivo de log: " + exception.getMessage());
        }
    }
}
