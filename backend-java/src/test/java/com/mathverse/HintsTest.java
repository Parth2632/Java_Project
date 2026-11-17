package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.mathverse.config.Settings;
import com.mathverse.service.HintWriter;
import com.mathverse.service.Hints;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Port of backend/tests/test_hints.py, the parts that need no API. The last
 * Python test (the whole game playable with no AI key) is ported with missions
 * in phase 3.
 */
class HintsTest {

    static final String QUESTION = "Solve:\n3Y + 5 = 20\n\nFind Y to disable the attack.";
    static final String PLAIN = "Move the constant to the other side first: subtract 5 on both sides, then divide by 3.";

    private static Settings settingsWithKey(String key) {
        return new Settings(new MockEnvironment()
                .withProperty("JWT_SECRET_KEY", "test-only-secret-not-used-anywhere-else")
                .withProperty("GROQ_API_KEY", key));
    }

    private static String hint(String key, HintWriter writer) {
        return new Hints(settingsWithKey(key), writer).hintFor(QUESTION, "linear_equations", PLAIN);
    }

    // test_without_an_api_key_the_built_in_hint_is_used
    @Test
    void withoutAnApiKeyTheBuiltInHintIsUsed() {
        HintWriter mustNotRun = (q, t, h) -> fail("the AI service must not be called");
        assertThat(hint("", mustNotRun)).isEqualTo(PLAIN);
    }

    // test_ai_wording_is_used_when_available
    @Test
    void aiWordingIsUsedWhenAvailable() {
        String styled = "Easy, Spidey: take 5 off both sides, then split by 3.";
        assertThat(hint("test-key", (q, t, h) -> styled)).isEqualTo(styled);
    }

    static Stream<String> badResults() {
        return Stream.of(null, "", "x".repeat(2000));
    }

    // test_unusable_ai_output_falls_back_to_the_built_in_hint
    @ParameterizedTest
    @MethodSource("badResults")
    void unusableAiOutputFallsBackToTheBuiltInHint(String badResult) {
        assertThat(hint("test-key", (q, t, h) -> badResult)).isEqualTo(PLAIN);
    }

    // test_ai_failure_falls_back_to_the_built_in_hint
    @Test
    void aiFailureFallsBackToTheBuiltInHint() {
        HintWriter boom = (q, t, h) -> {
            throw new TimeoutException("service too slow");
        };
        assertThat(hint("test-key", boom)).isEqualTo(PLAIN);
    }

    // test_hints_take_no_attention_input
    @Test
    void hintsTakeNoAttentionInput() throws NoSuchMethodException {
        Method hintFor = Hints.class.getMethod("hintFor", String.class, String.class, String.class);
        assertThat(Arrays.stream(hintFor.getParameters()).map(Parameter::getName))
                .containsExactly("questionText", "topic", "plainHint");
        String code = SourceText.serviceCode("Hints").toLowerCase(); // the class comment is skipped
        for (String word : List.of("cognitive", "onscreen", "on_screen", "facepresent", "face_present", "blink",
                "attention")) {
            assertThat(code).as(word).doesNotContain(word);
        }
    }
}
