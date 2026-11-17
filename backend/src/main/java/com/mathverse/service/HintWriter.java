package com.mathverse.service;

/**
 * Something that re-says a verified hint in Spider-Gwen's voice. The real one is
 * {@link GroqHintWriter}; tests use a stand-in (the Python tests replace _ask_model).
 */
public interface HintWriter {

    /** The reworded hint, or null if there is nothing usable. May throw on any failure. */
    String rephrase(String questionText, String topic, String plainHint) throws Exception;
}
