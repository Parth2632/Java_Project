package com.mathverse.service;

import com.mathverse.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gwen's hints (Python: services/hints.py).
 *
 * Every question already has a correct, plain hint written by the question
 * generator from the question's own numbers. That hint is always available.
 *
 * If a Groq API key is configured, an AI model is asked to do one thing only:
 * re-say that same hint in Spider-Gwen's voice. It is given the verified hint
 * and told not to add maths of its own or reveal the answer, so the model adds
 * character, not content. If the model is slow, fails, or returns something
 * unusable, the plain hint is returned instead. The game never depends on it.
 *
 * Hints depend on the question and the topic. They never depend on the camera or
 * on any attention estimate: hintFor has no parameter that could carry one.
 */
@Service
public class Hints {

    private static final Logger log = LoggerFactory.getLogger(Hints.class);
    static final int MAX_HINT_CHARS = 400;

    private final Settings settings;
    private final HintWriter writer;

    public Hints(Settings settings, HintWriter writer) {
        this.settings = settings;
        this.writer = writer;
    }

    /** The hint to show for a question. Always returns something useful. */
    public String hintFor(String questionText, String topic, String plainHint) {
        if (settings.groqApiKey().isEmpty()) {
            return plainHint;
        }
        String styled;
        try {
            styled = writer.rephrase(questionText, topic, plainHint);
        } catch (Exception e) {
            log.warn("AI hint unavailable; using the built-in hint ({})", e.toString());
            return plainHint;
        }
        if (styled == null || styled.isEmpty() || styled.length() > MAX_HINT_CHARS) {
            return plainHint;
        }
        return styled;
    }
}
