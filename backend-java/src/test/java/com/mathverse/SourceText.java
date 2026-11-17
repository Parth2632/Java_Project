package com.mathverse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a main source file, for tests that check what the code does NOT
 * mention (the Python tests use inspect.getsource for the same checks).
 */
public final class SourceText {

    private SourceText() {
    }

    /** The source of a class in com.mathverse.service, from its declaration on (the class comment is skipped). */
    public static String serviceCode(String className) {
        Path file = Path.of("src/main/java/com/mathverse/service", className + ".java");
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int start = text.indexOf("public final class " + className);
        if (start < 0) {
            start = text.indexOf("public class " + className);
        }
        if (start < 0) {
            throw new IllegalStateException("No class declaration in " + file);
        }
        return text.substring(start);
    }
}
