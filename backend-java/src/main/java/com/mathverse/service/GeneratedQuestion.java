package com.mathverse.service;

import java.util.List;

/**
 * A question with its four options and the index of the right one. The server
 * stores correctIndex; the client only ever receives the text and the options.
 */
public record GeneratedQuestion(String text, List<String> options, int correctIndex, String hint, String difficulty) {

    public GeneratedQuestion withDifficulty(String newDifficulty) {
        return new GeneratedQuestion(text, options, correctIndex, hint, newDifficulty);
    }
}
