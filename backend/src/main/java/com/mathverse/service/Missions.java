package com.mathverse.service;

import com.mathverse.config.ApiException;
import com.mathverse.dto.GameDtos.AnswerRequest;
import com.mathverse.dto.GameDtos.AnswerResponse;
import com.mathverse.dto.GameDtos.HintOut;
import com.mathverse.dto.GameDtos.QuestionOut;
import com.mathverse.dto.GameDtos.SessionState;
import com.mathverse.dto.GameDtos.StartSessionResponse;
import com.mathverse.model.Attempt;
import com.mathverse.model.AttemptRepository;
import com.mathverse.model.PlaySession;
import com.mathverse.model.PlaySessionRepository;
import com.mathverse.model.Times;
import com.mathverse.model.TopicMastery;
import com.mathverse.model.TopicMasteryRepository;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import com.mathverse.service.Curriculum.Boss;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gameplay (Python: routers/sessions.py). The server generates each question,
 * keeps the answer, judges the response and awards points. The client only
 * reports which option was picked.
 */
@Service
public class Missions {

    private final UserRepository users;
    private final PlaySessionRepository playSessions;
    private final AttemptRepository attempts;
    private final TopicMasteryRepository masteries;
    private final Progress progress;
    private final Hints hints;
    private final JsonMapper json = JsonMapper.builder().build();

    public Missions(UserRepository users, PlaySessionRepository playSessions, AttemptRepository attempts,
                    TopicMasteryRepository masteries, Progress progress, Hints hints) {
        this.users = users;
        this.playSessions = playSessions;
        this.attempts = attempts;
        this.masteries = masteries;
        this.progress = progress;
        this.hints = hints;
    }

    /** POST /sessions: start a mission against a boss and serve the first question. */
    @Transactional
    public StartSessionResponse start(User caller, String bossId) {
        User student = reload(caller);
        Boss boss = Curriculum.getBoss(bossId);
        if (boss == null) {
            throw new ApiException(404, "Unknown mission.");
        }
        if (!Curriculum.unlockedBosses(student.getLevel()).contains(boss.id())) {
            throw new ApiException(403, "Defeat the previous bosses to unlock this mission.");
        }
        // Only one mission at a time: anything left open is closed as abandoned.
        for (PlaySession stale : playSessions.findByStudentIdAndStatus(student.getId(), "active")) {
            stale.setStatus("abandoned");
            stale.setEndedAt(Times.utcnow());
        }
        PlaySession session = playSessions.save(new PlaySession(student.getId(), boss.id(), boss.topic()));
        Attempt attempt = serveQuestion(session);
        return new StartSessionResponse(SessionState.from(session), questionOut(attempt));
    }

    /**
     * GET /attempts/{id}/hint. Marks the question as "hint used" if it is still
     * open, then returns the hint. Not one transaction on purpose: the AI service
     * may take up to 6 seconds, and the database is not kept waiting for it.
     */
    public HintOut hint(User caller, long attemptId) {
        Attempt attempt = ownAttempt(caller, attemptId);
        if (attempt.getAnsweredAt() == null) {
            attempt.setHintUsed(true);
            attempts.save(attempt);
        }
        return new HintOut(hints.hintFor(attempt.getQuestionText(), attempt.getTopic(), attempt.getHintText()));
    }

    /** POST /attempts/{id}/answer: judge the answer, update mastery, points and the mission. */
    @Transactional
    public AnswerResponse answer(User caller, long attemptId, AnswerRequest request) {
        Attempt attempt = ownAttempt(caller, attemptId);
        PlaySession session = playSessions.findById(attempt.getSessionId()).orElseThrow();
        if (attempt.getAnsweredAt() != null) {
            throw new ApiException(409, "This question has already been answered.");
        }
        if (!session.getStatus().equals("active")) {
            throw new ApiException(409, "This mission is no longer active.");
        }
        User student = reload(caller);

        // ── Academic track: depends only on whether the maths was right ──────
        Integer selected = request.selectedIndex();
        boolean isCorrect = selected != null && selected == attempt.getCorrectIndex();
        TopicMastery topicMastery = masteryRow(student.getId(), attempt.getTopic());
        double masteryBefore = topicMastery.getPMastery();
        double masteryAfter = Mastery.updateMastery(masteryBefore, isCorrect);
        topicMastery.setPMastery(masteryAfter);
        topicMastery.setAttemptsCount(topicMastery.getAttemptsCount() + 1);

        int points = Game.pointsForAnswer(isCorrect, selected, session.getStreak(), request.responseTimeMs());

        attempt.setSelectedIndex(selected);
        attempt.setIsCorrect(isCorrect);
        attempt.setResponseTimeMs(request.responseTimeMs());
        attempt.setHintUsed(attempt.isHintUsed() || request.hintUsedOrFalse());
        attempt.setPointsAwarded(points);
        attempt.setMasteryBefore(masteryBefore);
        attempt.setMasteryAfter(masteryAfter);
        attempt.setAnsweredAt(Times.utcnow());

        // ── Attention track: stored as received (or left NULL), read by nothing here ──
        Attention.applyToAttempt(attempt, request.attention());

        if (isCorrect) {
            session.setCorrectCount(session.getCorrectCount() + 1);
            session.setStreak(session.getStreak() + 1);
        } else {
            session.setWrongCount(session.getWrongCount() + 1);
            session.setStreak(0);
        }

        int bonus = 0;
        if (session.getCorrectCount() >= Game.QUESTIONS_TO_WIN) {
            session.setStatus("victory");
            bonus = Game.victoryBonus(session.getStreak());
        } else if (session.getWrongCount() >= Game.MAX_WRONG) {
            session.setStatus("defeat");
        }
        if (!session.getStatus().equals("active")) {
            session.setEndedAt(Times.utcnow());
        }

        session.setPointsAwarded(session.getPointsAwarded() + points + bonus);
        student.setPoints(student.getPoints() + points + bonus);
        playSessions.flush(); // so the level count below sees this victory
        if (session.getStatus().equals("victory")) {
            student.setLevel(Math.max(student.getLevel(), progress.levelFor(student.getId())));
        }

        Attempt next = session.getStatus().equals("active") ? serveQuestion(session) : null;

        return new AnswerResponse(isCorrect, attempt.getCorrectIndex(), points, bonus, masteryAfter,
                MasteryDisplay.displayPct(masteryAfter), session.getStatus(), SessionState.from(session),
                progress.progressFor(student), next == null ? null : questionOut(next));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The caller as stored now, so points and level are updated on the current row. */
    private User reload(User caller) {
        return users.findById(caller.getId()).orElseThrow(ApiException::signInRequired);
    }

    /** 404 (not 403) for someone else's attempt, so ids cannot be probed. */
    private Attempt ownAttempt(User student, long attemptId) {
        Attempt attempt = attempts.findById(attemptId).orElse(null);
        if (attempt == null || !attempt.getStudentId().equals(student.getId())) {
            throw new ApiException(404, "Question not found.");
        }
        return attempt;
    }

    private TopicMastery masteryRow(long studentId, String topic) {
        return masteries.findByStudentIdAndTopic(studentId, topic)
                .orElseGet(() -> masteries.save(new TopicMastery(studentId, topic, Mastery.PRIOR)));
    }

    /** Generates the next question for a session and stores it, answer included. */
    private Attempt serveQuestion(PlaySession session) {
        double p = masteryRow(session.getStudentId(), session.getTopic()).getPMastery();
        GeneratedQuestion q = QuestionBank.questionOrBackup(session.getBossId(), Mastery.difficultyFor(p));
        Attempt attempt = new Attempt(session.getId(), session.getStudentId(), session.getTopic(), q.difficulty(),
                q.text(), json.writeValueAsString(q.options()), q.correctIndex(), q.hint());
        return attempts.save(attempt);
    }

    private QuestionOut questionOut(Attempt a) {
        List<String> options = json.readValue(a.getOptionsJson(), new TypeReference<List<String>>() {
        });
        return new QuestionOut(a.getId(), a.getQuestionText(), options, a.getTopic(), a.getDifficulty());
    }
}
