package com.spacewaltz.decrees.decree;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DecreeHistoryLogger {

    private static final Path LOG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("decrees_of_the_six");

    private static final Path LOG_PATH = LOG_DIR.resolve("decree_history.log");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void logStatusChange(Decree decree, DecreeStatus newStatus, String reasonTag) {
        try {
            if (!Files.exists(LOG_DIR)) {
                Files.createDirectories(LOG_DIR);
            }

            String timestamp = FORMATTER.format(Instant.now());

            // Avoid quotes breaking the line
            String safeTitle = decree.title == null
                    ? ""
                    : decree.title.replace('"', '\'');

            String line = String.format(
                    "[%s] id=%d status=%s reason=%s title=\"%s\"%n",
                    timestamp,
                    decree.id,
                    newStatus.name(),
                    reasonTag,
                    safeTitle
            );

            Files.writeString(
                    LOG_PATH,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("[Decrees] Failed to write decree history log", e);
        }
    }
}
