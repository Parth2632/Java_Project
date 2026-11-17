package com.mathverse.service;

import com.mathverse.config.Settings;
import com.mathverse.service.Curriculum.Boss;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the Groq AI service to reword a hint (Python: hints._ask_model).
 * Only used when GROQ_API_KEY is set. One request, no retries, 6 second limit.
 */
@Component
public class GroqHintWriter implements HintWriter {

    private static final URI ENDPOINT = URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private static final String SYSTEM_PROMPT =
            "You are Spider-Gwen talking to a school student over the radio during a maths game. "
                    + "You will be given a maths question and a correct hint for it. Rewrite the hint in your "
                    + "own friendly, encouraging voice in at most two short sentences. Keep every number and "
                    + "every step exactly as given. Do not add any other maths, do not solve the question, "
                    + "and do not state the final answer.";

    private final Settings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    // A plain mapper: the request uses the service's own field names.
    private final JsonMapper json = JsonMapper.builder().build();

    public GroqHintWriter(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String rephrase(String questionText, String topic, String plainHint)
            throws IOException, InterruptedException {
        Boss boss = Curriculum.bossForTopic(topic);
        String userMessage = "Villain: " + boss.name() + "\nTopic: " + boss.label() + "\nKey idea: " + boss.keyIdea()
                + "\n\nQuestion:\n" + questionText + "\n\nCorrect hint to rephrase:\n" + plainHint;
        Map<String, Object> body = Map.of(
                "model", settings.groqModel(),
                "temperature", 0.6,
                "max_tokens", 800, // the model spends some of these on hidden reasoning before it answers
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)));

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + settings.groqApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Groq answered with status " + response.statusCode());
        }
        JsonNode content = json.readTree(response.body()).path("choices").path(0).path("message").path("content");
        String text = content.isString() ? content.asString().strip() : "";
        if (text.contains("</think>")) { // some models prefix their reasoning
            text = text.substring(text.lastIndexOf("</think>") + "</think>".length()).strip();
        }
        return text.isEmpty() ? null : text;
    }
}
