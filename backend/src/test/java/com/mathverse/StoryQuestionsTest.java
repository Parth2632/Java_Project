package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mathverse.service.Curriculum;
import com.mathverse.service.GeneratedQuestion;
import com.mathverse.service.QuestionBank;
import com.mathverse.service.QuestionBank.BankQuestion;
import com.mathverse.service.Questions;
import com.mathverse.service.Rng;
import com.mathverse.service.StoryQuestions;
import com.mathverse.service.StoryQuestions.Story;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Port of backend/tests/test_story_questions.py, the parts that need no API.
 * Every villain has six stories of their own (two per tier), built from random
 * numbers with computed answers, and six fixed backup questions that are used
 * only if the generator fails. Nothing here depends on any AI service.
 * The Python test that wins a whole mission on backup questions goes through the
 * API and is ported with missions in phase 3.
 */
class StoryQuestionsTest {

    static final int SEEDS = 200;

    /** Words that tie a story to its villain's own world. */
    static final Map<String, String> WORLD = Map.of(
            "goblin", "Goblin|Oscorp|Osborn|pumpkin|[Gg]lider",
            "doc-ock", "Doc Ock|Doctor Octopus|Octavius|arms|reactor",
            "mysterio", "Mysterio|Beck|illusion|hologram|fishbowl|mirror|smoke",
            "venom", "Venom|symbiote|Eddie Brock",
            "lizard", "Lizard|Connors|serum|reptile|sewer",
            "kingpin", "Kingpin|Fisk|Vanessa|Hammerhead");

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("goblin", "Goblin|Osborn");
        NAMES.put("doc-ock", "Doc Ock|Octavius");
        NAMES.put("mysterio", "Mysterio|Beck");
        NAMES.put("venom", "Venom|Eddie Brock");
        NAMES.put("lizard", "Lizard|Connors");
        NAMES.put("kingpin", "Kingpin|Fisk");
    }

    /** The other villains' names, which must not appear in this villain's stories. */
    static String others(String boss) {
        return NAMES.entrySet().stream().filter(e -> !e.getKey().equals(boss)).map(Map.Entry::getValue)
                .collect(Collectors.joining("|"));
    }

    static boolean has(String regex, String text) {
        return Pattern.compile(regex).matcher(text).find();
    }

    /** The lines of a question after its title. */
    static List<String> bodyLines(String text) {
        String[] lines = text.split("\n", -1);
        return Arrays.stream(lines).skip(1).filter(line -> !line.isBlank()).toList();
    }

    record Made(String name, GeneratedQuestion question) {
    }

    static List<Made> storyQuestions(String boss, String tier) {
        List<Made> made = new ArrayList<>();
        for (Story story : StoryQuestions.STORIES.get(boss).get(tier)) {
            for (int seed = 0; seed < SEEDS; seed++) {
                made.add(new Made(story.name(), story.make().apply(new Rng(seed))));
            }
        }
        return made;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
        return QuestionsTest.cases();
    }

    @AfterEach
    void restoreGenerator() {
        QuestionBank.useGeneratorForTests(null);
    }

    // ── the stories ──────────────────────────────────────────────────────────

    // test_every_villain_has_six_stories_two_per_tier
    @Test
    void everyVillainHasSixStoriesTwoPerTier() {
        assertThat(StoryQuestions.STORIES.keySet()).containsExactlyInAnyOrderElementsOf(Curriculum.BOSS_IDS);
        List<String> all = new ArrayList<>();
        for (String boss : Curriculum.BOSS_IDS) {
            for (String tier : Questions.DIFFICULTIES) {
                List<Story> group = StoryQuestions.STORIES.get(boss).get(tier);
                assertThat(group).as(boss + " " + tier).hasSize(2);
                group.forEach(s -> all.add(s.name()));
            }
        }
        assertThat(all).hasSize(36).doesNotHaveDuplicates(); // no story is reused
    }

    // test_story_answers_are_correct_and_options_are_sound
    @ParameterizedTest
    @MethodSource("cases")
    void storyAnswersAreCorrectAndOptionsAreSound(String boss, String tier) {
        for (Made made : storyQuestions(boss, tier)) {
            GeneratedQuestion q = made.question();
            int answer = Solver.solve(boss, q.text());
            assertThat(q.options().get(q.correctIndex())).as("%s: %s %s", made.name(), q.text(), q.options())
                    .isEqualTo(String.valueOf(answer));
            assertThat(q.options()).as(made.name()).hasSize(4).doesNotHaveDuplicates();
            assertThat(answer).as("%s: %s", made.name(), q.text()).isPositive();
            assertThat(q.hint()).isNotEmpty();
            assertThat(QuestionsTest.statesAnswer(q.hint(), String.valueOf(answer))).as(q.hint()).isFalse();
        }
    }

    // test_stories_belong_to_their_own_villain
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void storiesBelongToTheirOwnVillain(String boss) {
        for (String tier : Questions.DIFFICULTIES) {
            for (Made made : storyQuestions(boss, tier)) {
                String text = made.question().text();
                assertThat(has(WORLD.get(boss), text)).as("%s: %s", made.name(), text).isTrue();
                assertThat(has(others(boss), text)).as("%s: %s", made.name(), text).isFalse();
            }
        }
    }

    // test_stories_are_short
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void storiesAreShort(String boss) {
        for (String tier : Questions.DIFFICULTIES) {
            for (Made made : storyQuestions(boss, tier)) {
                List<String> lines = bodyLines(made.question().text());
                assertThat(lines).as("%s: %s", made.name(), made.question().text()).hasSizeLessThanOrEqualTo(3);
                // Python counts characters (code points); √ and × are one character each either way.
                int length = lines.stream().mapToInt(line -> line.codePointCount(0, line.length())).sum();
                assertThat(length).as(made.name()).isLessThanOrEqualTo(200);
            }
        }
    }

    // test_stories_within_a_villain_are_all_different
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void storiesWithinAVillainAreAllDifferent(String boss) {
        Set<String> shapes = new HashSet<>();
        for (String tier : Questions.DIFFICULTIES) {
            for (Story story : StoryQuestions.STORIES.get(boss).get(tier)) {
                shapes.add(story.make().apply(new Rng(1)).text().replaceAll("\\d+", "N"));
            }
        }
        assertThat(shapes).hasSize(6);
    }

    // test_the_game_mixes_stories_with_plain_questions
    @Test
    void theGameMixesStoriesWithPlainQuestions() {
        for (String boss : Curriculum.BOSS_IDS) {
            for (String tier : Questions.DIFFICULTIES) {
                Set<String> titles = new HashSet<>();
                for (int seed = 0; seed < 120; seed++) {
                    titles.add(Questions.generate(boss, tier, new Rng(seed)).text().split("\n")[0]);
                }
                Set<String> storyTitles = new HashSet<>();
                for (Story story : StoryQuestions.STORIES.get(boss).get(tier)) {
                    storyTitles.add(story.make().apply(new Rng(0)).text().split("\n")[0]);
                }
                assertThat(titles).as(boss + " " + tier).containsAll(storyTitles); // both stories turn up
                Set<String> plain = new HashSet<>(titles);
                plain.removeAll(storyTitles);
                assertThat(plain).as(boss + " " + tier).isNotEmpty(); // and so do the plain questions
            }
        }
    }

    // test_stories_change_their_numbers_every_time
    @Test
    void storiesChangeTheirNumbersEveryTime() {
        for (String boss : Curriculum.BOSS_IDS) {
            for (String tier : Questions.DIFFICULTIES) {
                for (Story story : StoryQuestions.STORIES.get(boss).get(tier)) {
                    Set<String> texts = new HashSet<>();
                    for (int seed = 0; seed < 40; seed++) {
                        texts.add(story.make().apply(new Rng(seed)).text());
                    }
                    assertThat(texts.size()).as(story.name()).isGreaterThanOrEqualTo(4);
                }
            }
        }
    }

    // ── the backup bank ──────────────────────────────────────────────────────

    // test_every_villain_has_six_backup_questions_two_per_tier
    @Test
    void everyVillainHasSixBackupQuestionsTwoPerTier() {
        assertThat(QuestionBank.BANK.keySet()).containsExactlyInAnyOrderElementsOf(Curriculum.BOSS_IDS);
        for (String boss : Curriculum.BOSS_IDS) {
            assertThat(QuestionBank.BANK.get(boss).stream().map(BankQuestion::difficulty))
                    .containsExactlyInAnyOrder("easy", "easy", "medium", "medium", "hard", "hard");
        }
        List<String> texts = QuestionBank.BANK.values().stream().flatMap(List::stream).map(BankQuestion::text).toList();
        assertThat(texts).hasSize(36).doesNotHaveDuplicates();
    }

    // test_every_backup_question_is_correct_short_and_in_character
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void everyBackupQuestionIsCorrectShortAndInCharacter(String boss) {
        for (BankQuestion q : QuestionBank.BANK.get(boss)) {
            assertThat(Solver.solve(boss, q.text())).as(q.text()).isEqualTo(q.answer());
            assertThat(q.wrong()).as(q.text()).hasSize(3).doesNotContain(q.answer()).doesNotHaveDuplicates();
            assertThat(q.wrong()).as(q.text()).allMatch(w -> w > 0);
            assertThat(bodyLines(q.text())).as(q.text()).hasSizeLessThanOrEqualTo(3);
            assertThat(has(WORLD.get(boss), q.text()) && !has(others(boss), q.text())).as(q.text()).isTrue();
            assertThat(q.hint()).isNotEmpty();
            assertThat(QuestionsTest.statesAnswer(q.hint(), String.valueOf(q.answer()))).as(q.hint()).isFalse();
        }
    }

    // test_a_backup_question_is_served_ready_to_play
    @ParameterizedTest
    @MethodSource("cases")
    void aBackupQuestionIsServedReadyToPlay(String boss, String tier) {
        Set<String> seen = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (int seed = 0; seed < 60; seed++) {
            GeneratedQuestion q = QuestionBank.backupQuestion(boss, tier, new Rng(seed));
            assertThat(q.difficulty()).isEqualTo(tier);
            assertThat(q.options()).doesNotHaveDuplicates().hasSize(4);
            assertThat(q.options().get(q.correctIndex())).isEqualTo(String.valueOf(Solver.solve(boss, q.text())));
            seen.add(q.text());
            positions.add(q.correctIndex());
        }
        assertThat(seen).hasSize(2); // both questions of the tier are used
        assertThat(positions).containsExactlyInAnyOrder(0, 1, 2, 3); // and the answer moves around
    }

    // test_backup_rejects_an_unknown_villain_or_tier
    @Test
    void backupRejectsAnUnknownVillainOrTier() {
        assertThatThrownBy(() -> QuestionBank.backupQuestion("thanos", "medium"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuestionBank.backupQuestion("goblin", "impossible"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── falling back ─────────────────────────────────────────────────────────

    // test_normally_the_generator_is_used
    @Test
    void normallyTheGeneratorIsUsed() {
        AtomicInteger calls = new AtomicInteger();
        List<GeneratedQuestion> made = new ArrayList<>();
        QuestionBank.useGeneratorForTests((boss, tier, rng) -> {
            calls.incrementAndGet();
            GeneratedQuestion q = Questions.generate(boss, tier, rng);
            made.add(q);
            return q;
        });
        for (String boss : Curriculum.BOSS_IDS) {
            GeneratedQuestion served = QuestionBank.questionOrBackup(boss, "medium", new Rng(3));
            assertThat(served.text()).isNotEmpty();
            assertThat(served).isSameAs(made.getLast()); // what was served came from the generator, not the bank
        }
        assertThat(calls.get()).isEqualTo(6);
    }

    // test_if_the_generator_breaks_a_backup_question_is_served
    @ParameterizedTest
    @MethodSource("cases")
    void ifTheGeneratorBreaksABackupQuestionIsServed(String boss, String tier) {
        QuestionBank.useGeneratorForTests((b, t, rng) -> {
            throw new IllegalStateException("generator is broken");
        });
        GeneratedQuestion q = QuestionBank.questionOrBackup(boss, tier);
        Set<String> bankTexts = QuestionBank.BANK.get(boss).stream().filter(b -> b.difficulty().equals(tier))
                .map(BankQuestion::text).collect(Collectors.toSet());
        assertThat(bankTexts).contains(q.text());
        assertThat(q.options().get(q.correctIndex())).isEqualTo(String.valueOf(Solver.solve(boss, q.text())));
    }

    // test_an_unknown_villain_is_still_an_error_not_a_backup
    @Test
    void anUnknownVillainIsStillAnErrorNotABackup() {
        assertThatThrownBy(() -> QuestionBank.questionOrBackup("thanos", "medium"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // test_questions_never_touch_the_ai_service
    /** Questions come from code alone, so an AI outage cannot affect them. */
    @Test
    void questionsNeverTouchTheAiService() {
        for (String boss : Curriculum.BOSS_IDS) {
            for (String tier : Questions.DIFFICULTIES) {
                assertThat(QuestionBank.questionOrBackup(boss, tier, new Rng(5)).options()).isNotEmpty();
            }
        }
        for (String file : List.of("Questions", "StoryQuestions", "QuestionBank")) {
            String code = SourceText.serviceCode(file).toLowerCase();
            assertThat(code).as(file).doesNotContain("groq").doesNotContain("hintwriter").doesNotContain("hints.");
        }
    }
}
