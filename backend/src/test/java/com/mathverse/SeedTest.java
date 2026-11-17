package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.Attempt;
import com.mathverse.model.PlaySession;
import com.mathverse.model.User;
import com.mathverse.service.DemoData;
import com.mathverse.service.Mastery;
import com.mathverse.service.SeedCommand;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Java-only checks of the demo data and the seed command (Python: seed.py has
 * no test file of its own; test_auth.py and test_guardians.py use it).
 * The --reset case runs only against the in-memory test database.
 */
class SeedTest extends ApiTest {

    @Autowired DemoData demoData;
    @Autowired SeedCommand seed;

    @Test
    void demoHistoryFollowsTheGameRules() {
        assertThat(demoData.createDemoAccounts()).isTrue();
        User student = users.findDemoAccount("student").orElseThrow();
        assertThat(student.getLinkCode()).isEqualTo("SPIDER-DEMO01");
        assertThat(student.getLevel()).isEqualTo(3); // goblin and doc-ock won, mysterio lost

        List<PlaySession> sessions = playSessions.findByStudentId(student.getId());
        assertThat(sessions.stream().map(PlaySession::getStatus)).containsExactlyInAnyOrder("victory", "victory",
                "defeat");
        int sessionPoints = sessions.stream().mapToInt(PlaySession::getPointsAwarded).sum();
        assertThat(student.getPoints()).isEqualTo(sessionPoints);

        List<Attempt> rows = attempts.findByStudentIdOrderById(student.getId());
        assertThat(rows).hasSize(30);
        for (Attempt a : rows) {
            // mastery follows from correctness alone, as in the live game
            assertThat(a.getMasteryAfter()).isEqualTo(Mastery.updateMastery(a.getMasteryBefore(), a.getIsCorrect()));
            // camera missions have all attention numbers; the doc-ock mission has none, never 0
            boolean camera = !a.getTopic().equals("area_perimeter");
            assertThat(a.getOnScreenPct() != null).isEqualTo(camera);
            assertThat(a.getAttentionLabel() != null).isEqualTo(camera);
        }
    }

    @Test
    void demoDataIsTheSameEveryTime() {
        demoData.createDemoAccounts();
        List<String> first = attempts.findAll().stream().map(Attempt::getQuestionText).toList();
        emptyDatabase();
        demoData.createDemoAccounts();
        List<String> second = attempts.findAll().stream().map(Attempt::getQuestionText).toList();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void seedCommandRecognisesItsFlags() {
        assertThat(SeedCommand.isSeedRun(new String[] {"--demo"})).isTrue();
        assertThat(SeedCommand.isSeedRun(new String[] {"--reset"})).isTrue();
        assertThat(SeedCommand.isSeedRun(new String[] {})).isFalse();
        assertThat(SeedCommand.isSeedRun(new String[] {"--server.port=9000"})).isFalse();
    }

    @Test
    void demoFlagCreatesAccountsOnceAndResetEmptiesEverything() {
        seed.run(new String[] {"--demo"});
        seed.run(new String[] {"--demo"}); // safe to re-run
        assertThat(users.countDemoAccounts()).isEqualTo(3);
        register("student", "Real Child");

        seed.run(new String[] {"--reset"});
        assertThat(users.count()).isZero();
        assertThat(attempts.count()).isZero();
        assertThat(playSessions.count()).isZero();
        register("student", "After Reset"); // the tables are back and work
        assertThat(users.count()).isEqualTo(1);
    }
}
