package com.mathverse.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a .env file of NAME=value lines. Blank lines and lines starting with #
 * are skipped; surrounding quotes are removed. A missing file gives no values.
 */
public final class EnvFile {

    private EnvFile() {
    }

    public static Map<String, Object> read(String fileName) {
        Map<String, Object> values = new HashMap<>();
        Path path = Path.of(fileName);
        if (!Files.isRegularFile(path)) {
            return values;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path.toAbsolutePath(), e);
        }
        for (String raw : lines) {
            String line = raw.strip();
            int equals = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || equals < 1) {
                continue;
            }
            String name = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            boolean quoted = value.length() >= 2
                    && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"));
            if (quoted) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(name, value);
        }
        return values;
    }
}
