package com.mathverse.service;

import com.mathverse.model.Attempt;
import com.mathverse.model.AttemptRepository;
import com.mathverse.model.GuardianLink;
import com.mathverse.model.GuardianLinkRepository;
import com.mathverse.model.PlaySession;
import com.mathverse.model.PlaySessionRepository;
import com.mathverse.model.Roles;
import com.mathverse.model.Times;
import com.mathverse.model.TopicMastery;
import com.mathverse.model.TopicMasteryRepository;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import com.mathverse.security.Passwords;
import com.mathverse.service.Curriculum.Boss;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo accounts and their synthetic practice history (Python: seed.py).
 *
 * Demo accounts are marked is_demo = true and can only be reached through
 * POST /auth/demo/{role} when DEMO_MODE=true. Their passwords are random and
 * never shown, so they cannot be used with normal login. The history is
 * written by replaying three missions with the same rules as the live game;
 * it is plainly sample data, kept apart from real accounts.
 */
@Service
public class DemoData {

    public static final String EMAIL_DOMAIN = "mathverse.demo";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One replayed mission: boss, days ago, camera used?, answers in order (true = correct). */
    record DemoSession(String bossId, int daysAgo, boolean camera, List<Boolean> answers) {
    }

    private static final boolean T = true;
    private static final boolean F = false;
    static final List<DemoSession> DEMO_SESSIONS = List.of(
            new DemoSession("goblin", 9, true, List.of(T, T, F, T, T, T, F, T, T)),
            new DemoSession("doc-ock", 6, false, List.of(T, F, T, T, F, T, T, F, T, T)),
            new DemoSession("mysterio", 2, true, List.of(F, T, F, F, T, F, T, F, T, F, F)));

    private final UserRepository users;
    private final GuardianLinkRepository links;
    private final PlaySessionRepository playSessions;
    private final AttemptRepository attempts;
    private final TopicMasteryRepository masteries;
    private final Passwords passwords;
    private final Progress progress;
    private final JsonMapper json = JsonMapper.builder().build();

    public DemoData(UserRepository users, GuardianLinkRepository links, PlaySessionRepository playSessions,
                    AttemptRepository attempts, TopicMasteryRepository masteries, Passwords passwords,
                    Progress progress) {
        this.users = users;
        this.links = links;
        this.playSessions = playSessions;
        this.attempts = attempts;
        this.masteries = masteries;
        this.passwords = passwords;
        this.progress = progress;
    }

    /** Creates the demo student, parent and teacher with history. Returns false if they already exist. */
    @Transactional
    public boolean createDemoAccounts() {
        if (users.countDemoAccounts() > 0) {
            return false;
        }
        User student = demoUser("Peter Parker (Demo)", Roles.STUDENT, "SPIDER-DEMO01");
        User parent = demoUser("Aunt May (Demo)", Roles.PARENT, null);
        User teacher = demoUser("Prof. Connors (Demo)", Roles.TEACHER, null);
        links.save(new GuardianLink(parent.getId(), student.getId()));
        links.save(new GuardianLink(teacher.getId(), student.getId()));

        Rng rng = new Rng(2026); // fixed seed: the demo data is the same every time
        for (DemoSession demo : DEMO_SESSIONS) {
            replaySession(student, demo, rng);
        }
        return true;
    }

    private User demoUser(String name, String role, String linkCode) {
        byte[] secret = new byte[24];
        RANDOM.nextBytes(secret);
        String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        User user = new User(name, "demo." + role + "@" + EMAIL_DOMAIN, passwords.hash(randomPassword),
                role, linkCode, true);
        return users.save(user);
    }

    /** Writes one synthetic mission using the same rules as the live game. */
    private void replaySession(User student, DemoSession demo, Rng rng) {
        Boss boss = Curriculum.getBoss(demo.bossId());
        LocalDateTime clock = Times.utcnow().minusDays(demo.daysAgo()).minusHours(rng.randint(1, 5));
        PlaySession session = new PlaySession(student.getId(), boss.id(), boss.topic());
        session.setStartedAt(clock);
        playSessions.save(session);

        TopicMastery tm = masteries.findByStudentIdAndTopic(student.getId(), boss.topic())
                .orElseGet(() -> masteries.save(new TopicMastery(student.getId(), boss.topic(), Mastery.PRIOR)));

        for (boolean isCorrect : demo.answers()) {
            GeneratedQuestion q = Questions.generate(boss.id(), Mastery.difficultyFor(tm.getPMastery()), rng);
            List<Integer> wrongChoices = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if (i != q.correctIndex()) {
                    wrongChoices.add(i);
                }
            }
            int selected = isCorrect ? q.correctIndex() : rng.choice(wrongChoices);
            int responseMs = rng.randint(6, 40) * 1000;
            int points = Game.pointsForAnswer(isCorrect, selected, session.getStreak(), responseMs);
            double before = tm.getPMastery();
            tm.setPMastery(Mastery.updateMastery(before, isCorrect));
            tm.setAttemptsCount(tm.getAttemptsCount() + 1);

            clock = clock.plusNanos((responseMs + 3000) * 1_000_000L);
            Attempt attempt = new Attempt(session.getId(), student.getId(), boss.topic(), q.difficulty(), q.text(),
                    json.writeValueAsString(q.options()), q.correctIndex(), q.hint());
            attempt.setSelectedIndex(selected);
            attempt.setIsCorrect(isCorrect);
            attempt.setResponseTimeMs(responseMs);
            attempt.setHintUsed(!isCorrect && rng.random() < 0.4);
            attempt.setPointsAwarded(points);
            attempt.setMasteryBefore(before);
            attempt.setMasteryAfter(tm.getPMastery());
            attempt.setServedAt(clock.minusNanos(responseMs * 1_000_000L));
            attempt.setAnsweredAt(clock);
            if (demo.camera()) { // missions without the camera keep every attention column NULL
                double face = rng.randint(85, 100);
                double onScreen = rng.randint(45, 98);
                attempt.setFacePresentPct(face);
                attempt.setOnScreenPct(onScreen);
                attempt.setBlinkRatePerMin((double) rng.randint(10, 24));
                Attention.ScoreAndLabel result = Attention.scoreAndLabel(face, onScreen);
                attempt.setAttentionScore(result.score());
                attempt.setAttentionLabel(result.label());
            }
            attempts.save(attempt);

            if (isCorrect) {
                session.setCorrectCount(session.getCorrectCount() + 1);
                session.setStreak(session.getStreak() + 1);
            } else {
                session.setWrongCount(session.getWrongCount() + 1);
                session.setStreak(0);
            }
            session.setPointsAwarded(session.getPointsAwarded() + points);
        }

        if (session.getCorrectCount() >= Game.QUESTIONS_TO_WIN) {
            session.setStatus("victory");
            session.setPointsAwarded(session.getPointsAwarded() + Game.victoryBonus(session.getStreak()));
        } else if (session.getWrongCount() >= Game.MAX_WRONG) {
            session.setStatus("defeat");
        } else {
            session.setStatus("abandoned");
        }
        session.setEndedAt(clock);
        student.setPoints(student.getPoints() + session.getPointsAwarded());
        playSessions.flush(); // so the level count sees this mission
        student.setLevel(progress.levelFor(student.getId()));
    }
}
