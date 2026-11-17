package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.User;
import com.mathverse.service.Curriculum;
import com.mathverse.service.Mastery;
import com.mathverse.service.QuestionBank;
import com.mathverse.service.QuestionBank.BankQuestion;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import tools.jackson.databind.JsonNode;

/**
 * The API tests of three Python files whose other tests are plain unit tests:
 *   test_mastery_display.py::test_difficulty_still_follows_the_raw_value
 *   test_hints.py::test_game_is_fully_playable_with_no_ai_service
 *   test_story_questions.py::test_a_whole_mission_can_be_won_on_backup_questions_alone (6 bosses)
 */
class GameApiTest extends ApiTest {

    @AfterEach
    void restoreGenerator() {
        QuestionBank.useGeneratorForTests(null);
    }

    // test_mastery_display.py::test_difficulty_still_follows_the_raw_value
    @Test
    void difficultyStillFollowsTheRawValue() {
        Account student = register();
        JsonNode question = start(student).json().get("question");
        for (int i = 0; i < 4; i++) {
            JsonNode body = play(student, question.get("attempt_id").asLong(), true);
            question = body.get("next_question");
            assertThat(question.get("difficulty").asString())
                    .isEqualTo(Mastery.difficultyFor(body.get("mastery").asDouble()));
        }
    }

    // test_hints.py::test_game_is_fully_playable_with_no_ai_service
    /** No GROQ_API_KEY (the test settings have none): missions, hints and victory all still work. */
    @Test
    void gameIsFullyPlayableWithNoAiService() {
        assertThat(settings.groqApiKey()).isEmpty();
        Account student = register();
        JsonNode res = start(student).json();
        Response hint = getPath("/attempts/" + res.get("question").get("attempt_id").asLong() + "/hint", student);
        assertThat(hint.status()).isEqualTo(200);
        assertThat(hint.json().get("hint").asString().length()).isGreaterThan(10);
        assertThat(winMission(student).get("session_status").asString()).isEqualTo("victory");
    }

    // test_story_questions.py::test_a_whole_mission_can_be_won_on_backup_questions_alone
    /** With the generator broken and no AI key, every villain is still fully playable. */
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void aWholeMissionCanBeWonOnBackupQuestionsAlone(String bossId) {
        QuestionBank.useGeneratorForTests((boss, tier, rng) -> {
            throw new IllegalStateException("generator is broken");
        });
        Account student = register("student");
        User row = users.findById(student.id()).orElseThrow(); // unlock every villain for this test
        row.setLevel(Curriculum.BOSS_IDS.size());
        users.save(row);

        Response res = start(student, bossId);
        assertThat(res.status()).as(res.text()).isEqualTo(201);
        Set<String> bankTexts = QuestionBank.BANK.get(bossId).stream().map(BankQuestion::text)
                .collect(Collectors.toSet());
        JsonNode question = res.json().get("question");
        JsonNode body = null;
        int served = 0;
        while (question != null && !question.isNull()) {
            assertThat(bankTexts).contains(question.get("text").asString());
            served++;
            Response hint = getPath("/attempts/" + question.get("attempt_id").asLong() + "/hint", student);
            assertThat(hint.status()).isEqualTo(200);
            assertThat(hint.json().get("hint").asString()).isNotEmpty();
            body = play(student, question.get("attempt_id").asLong(), true);
            question = body.get("next_question");
        }
        assertThat(body.get("session_status").asString()).isEqualTo("victory");
        assertThat(served).isEqualTo(7);
    }
}
