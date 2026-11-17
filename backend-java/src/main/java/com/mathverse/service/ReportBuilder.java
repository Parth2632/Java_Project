package com.mathverse.service;

import com.mathverse.dto.ReportDtos.AttentionOut;
import com.mathverse.dto.ReportDtos.Badge;
import com.mathverse.dto.ReportDtos.DailyPoint;
import com.mathverse.dto.ReportDtos.LinkedStudent;
import com.mathverse.dto.ReportDtos.MasteryPoint;
import com.mathverse.dto.ReportDtos.RecentAttemptOut;
import com.mathverse.dto.ReportDtos.StudentReport;
import com.mathverse.dto.ReportDtos.TopicHistory;
import com.mathverse.dto.ReportDtos.TopicMasteryOut;
import com.mathverse.model.Attempt;
import com.mathverse.model.AttemptRepository;
import com.mathverse.model.PlaySession;
import com.mathverse.model.PlaySessionRepository;
import com.mathverse.model.Times;
import com.mathverse.model.TopicMastery;
import com.mathverse.model.TopicMasteryRepository;
import com.mathverse.model.User;
import com.mathverse.service.Curriculum.Boss;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a student's report from stored rows (Python: services/report_builder.py).
 *
 * Rules this class keeps:
 * - Every number is counted from the database. Where there is no data the value
 *   is null, 0 or an empty list; nothing is filled in with sample values.
 * - Learning progress (accuracy, mastery, trends, badges, observations) uses only
 *   maths answers. The attention estimate is reported in its own section and is
 *   never combined with, or used to explain, any academic number.
 * - Questions where attention was not measured are counted as "not measured".
 *   They are left out of attention averages, never treated as zero.
 */
@Service
public class ReportBuilder {

    static final int RECENT_LIMIT = 10;
    public static final int TREND_DAYS = 14;
    static final int MIN_ANSWERS_FOR_OBSERVATIONS = 10;
    static final int MIN_ANSWERS_PER_TOPIC = 5;
    static final int MIN_ANSWERS_PER_WEEK = 5;
    static final List<Integer> QUESTION_MILESTONES = List.of(25, 100, 250);

    private final AttemptRepository attempts;
    private final PlaySessionRepository playSessions;
    private final TopicMasteryRepository masteries;

    public ReportBuilder(AttemptRepository attempts, PlaySessionRepository playSessions,
                         TopicMasteryRepository masteries) {
        this.attempts = attempts;
        this.playSessions = playSessions;
        this.masteries = masteries;
    }

    /** part as a whole-number percentage of whole; null when whole is 0 (unknown, not 0%). */
    static Integer pct(long part, long whole) {
        return whole == 0 ? null : Rounding.round(100.0 * part / whole);
    }

    private static boolean correct(Attempt a) {
        return Boolean.TRUE.equals(a.getIsCorrect());
    }

    private static long count(List<Attempt> rows, java.util.function.Predicate<Attempt> test) {
        return rows.stream().filter(test).count();
    }

    /** Average of the measured on-screen percentages, rounded; null when none were measured. */
    private static Integer averageOnScreen(List<Attempt> measured) {
        if (measured.isEmpty()) {
            return null;
        }
        double sum = measured.stream().mapToDouble(Attempt::getOnScreenPct).sum();
        return Rounding.round(sum / measured.size());
    }

    private static long totalMs(List<Attempt> rows) {
        return rows.stream().mapToLong(a -> a.getResponseTimeMs() == null ? 0 : a.getResponseTimeMs()).sum();
    }

    /** The calendar day of an answer, on the viewer's clock. */
    private static LocalDate dayOf(Attempt a, int tzOffsetMinutes) {
        return a.getAnsweredAt().plusMinutes(tzOffsetMinutes).toLocalDate();
    }

    /** The one-line summary shown in a parent's or teacher's list of students. */
    @Transactional(readOnly = true)
    public LinkedStudent summaryForList(User student) {
        List<Attempt> answered = attempts.findAnswered(student.getId());
        return new LinkedStudent(student.getId(), student.getName(), student.getLevel(), student.getPoints(),
                student.isDemo(), answered.size(), pct(count(answered, ReportBuilder::correct), answered.size()),
                answered.isEmpty() ? null : Times.iso(answered.getFirst().getAnsweredAt()));
    }

    @Transactional(readOnly = true)
    public StudentReport build(User student, int tzOffsetMinutes) {
        return build(student, tzOffsetMinutes, Times.utcnow());
    }

    /** The full report. tzOffsetMinutes shifts days to the viewer's clock, so "today" is their day. */
    @Transactional(readOnly = true)
    public StudentReport build(User student, int tzOffsetMinutes, LocalDateTime now) {
        LocalDate today = now.plusMinutes(tzOffsetMinutes).toLocalDate();

        List<Attempt> answered = attempts.findAnswered(student.getId()); // newest first
        int total = answered.size();
        long correctCount = count(answered, ReportBuilder::correct);

        List<PlaySession> sessions = playSessions.findByStudentId(student.getId());
        List<PlaySession> won = sessions.stream().filter(s -> s.getStatus().equals("victory")).toList();

        Map<String, TopicMastery> masteryRows = new HashMap<>();
        for (TopicMastery m : masteries.findByStudentId(student.getId())) {
            masteryRows.put(m.getTopic(), m);
        }
        Map<String, Integer> correctByTopic = new HashMap<>();
        for (Attempt a : answered) {
            if (correct(a)) {
                correctByTopic.merge(a.getTopic(), 1, Integer::sum);
            }
        }

        List<TopicMasteryOut> topics = new ArrayList<>();
        Map<String, Double> rawMastery = new HashMap<>(); // stored values, used for every decision below
        for (Boss boss : Curriculum.BOSSES) {
            TopicMastery row = masteryRows.get(boss.topic());
            boolean started = row != null && row.getAttemptsCount() > 0;
            if (started) {
                rawMastery.put(boss.topic(), row.getPMastery());
            }
            topics.add(new TopicMasteryOut(boss.topic(), boss.label(),
                    started ? MasteryDisplay.displayPct(row.getPMastery()) : null,
                    started ? MasteryDisplay.masteryBand(row.getPMastery()) : null,
                    row == null ? 0 : row.getAttemptsCount(),
                    correctByTopic.getOrDefault(boss.topic(), 0)));
        }

        List<DailyPoint> daily = daily(answered, today, tzOffsetMinutes);
        List<Attempt> measured = answered.stream().filter(a -> a.getOnScreenPct() != null).toList();

        List<RecentAttemptOut> recent = new ArrayList<>();
        for (Attempt a : answered.subList(0, Math.min(RECENT_LIMIT, total))) {
            Boss boss = Curriculum.bossForTopic(a.getTopic());
            int ms = a.getResponseTimeMs() == null ? 0 : a.getResponseTimeMs();
            recent.add(new RecentAttemptOut(a.getId(), boss.name(), boss.label(), a.getDifficulty(), correct(a),
                    Rounding.round(ms / 1000.0, 1), a.isHintUsed(), a.getPointsAwarded(),
                    Times.iso(a.getAnsweredAt()), a.getAttentionLabel()));
        }

        List<TopicMasteryOut> needsPractice = new ArrayList<>(topics.stream()
                .filter(t -> rawMastery.containsKey(t.topic()) && rawMastery.get(t.topic()) < Mastery.EASY_BELOW)
                .toList());
        needsPractice.sort(Comparator.comparingDouble(t -> rawMastery.get(t.topic()))); // weakest first

        Set<String> bossesDefeated = new HashSet<>();
        for (PlaySession s : won) {
            if (Curriculum.getBoss(s.getBossId()) != null) {
                bossesDefeated.add(s.getBossId());
            }
        }

        return new StudentReport(
                student.getId(),
                student.getName(),
                student.isDemo(),
                student.getPoints(),
                student.getLevel(),
                total == 0 ? null : Times.iso(answered.getFirst().getAnsweredAt()),
                total,
                (int) correctCount,
                pct(correctCount, total),
                Rounding.round(totalMs(answered) / 60000.0),
                (int) sessions.stream().filter(s -> !s.getStatus().equals("active")).count(),
                won.size(),
                bossesDefeated.size(),
                Curriculum.BOSSES.size(),
                topics,
                needsPractice,
                daily,
                masteryHistory(answered, tzOffsetMinutes),
                observations(answered, topics, rawMastery, daily),
                badges(total, sessions),
                recent,
                new AttentionOut(measured.size(), total, averageOnScreen(measured)));
    }

    /** One entry per day for the last TREND_DAYS days, including days with no practice. */
    private static List<DailyPoint> daily(List<Attempt> answered, LocalDate today, int tzOffsetMinutes) {
        Map<LocalDate, List<Attempt>> byDay = new HashMap<>();
        for (Attempt a : answered) {
            byDay.computeIfAbsent(dayOf(a, tzOffsetMinutes), d -> new ArrayList<>()).add(a);
        }
        List<DailyPoint> points = new ArrayList<>();
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            List<Attempt> rows = byDay.getOrDefault(day, List.of());
            List<Attempt> measured = rows.stream().filter(a -> a.getOnScreenPct() != null).toList();
            long right = count(rows, ReportBuilder::correct);
            points.add(new DailyPoint(day.toString(), rows.size(), (int) right, pct(right, rows.size()),
                    Rounding.round(totalMs(rows) / 60000.0, 1), measured.size(), averageOnScreen(measured)));
        }
        return points;
    }

    /** For each topic that has been played: mastery at the end of each day it was practised. */
    private static List<TopicHistory> masteryHistory(List<Attempt> answered, int tzOffsetMinutes) {
        Map<String, TreeMap<LocalDate, Double>> endOfDay = new HashMap<>();
        for (Attempt a : answered.reversed()) { // oldest first, so later answers overwrite earlier ones that day
            if (a.getMasteryAfter() != null) {
                endOfDay.computeIfAbsent(a.getTopic(), t -> new TreeMap<>())
                        .put(dayOf(a, tzOffsetMinutes), a.getMasteryAfter());
            }
        }
        List<TopicHistory> history = new ArrayList<>();
        for (Boss boss : Curriculum.BOSSES) {
            TreeMap<LocalDate, Double> days = endOfDay.get(boss.topic());
            if (days != null) {
                List<MasteryPoint> points = new ArrayList<>();
                days.forEach((d, p) -> points.add(new MasteryPoint(d.toString(), MasteryDisplay.displayPct(p))));
                history.add(new TopicHistory(boss.topic(), boss.label(), points));
            }
        }
        return history;
    }

    /** Badges are earned from stored missions and answers. None are handed out by default. */
    private static List<Badge> badges(int answeredCount, List<PlaySession> sessions) {
        List<PlaySession> won = sessions.stream().filter(s -> s.getStatus().equals("victory")).toList();
        List<Badge> badges = new ArrayList<>();
        for (Boss boss : Curriculum.BOSSES) {
            if (won.stream().anyMatch(s -> s.getBossId().equals(boss.id()))) {
                badges.add(new Badge("defeated-" + boss.id(), boss.name() + " defeated",
                        "Won a mission on " + boss.label() + "."));
            }
        }
        if (won.stream().anyMatch(s -> s.getWrongCount() == 0)) {
            badges.add(new Badge("flawless", "Flawless mission", "Won a mission without a single wrong answer."));
        }
        for (int milestone : QUESTION_MILESTONES) {
            if (answeredCount >= milestone) {
                badges.add(new Badge("questions-" + milestone, milestone + " questions answered",
                        "Answered " + milestone + " questions in total."));
            }
        }
        return badges;
    }

    /**
     * Short factual statements produced by fixed rules on the numbers above.
     * No AI is involved, and attention data is never used here.
     */
    private static List<String> observations(List<Attempt> answered, List<TopicMasteryOut> topics,
                                             Map<String, Double> rawMastery, List<DailyPoint> daily) {
        if (answered.size() < MIN_ANSWERS_FOR_OBSERVATIONS) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();

        List<DailyPoint> thisWeek = daily.subList(daily.size() - 7, daily.size());
        List<DailyPoint> lastWeek = daily.subList(daily.size() - 14, daily.size() - 7);
        int qNow = thisWeek.stream().mapToInt(DailyPoint::questions).sum();
        int qBefore = lastWeek.stream().mapToInt(DailyPoint::questions).sum();
        long daysPractised = thisWeek.stream().filter(d -> d.questions() > 0).count();
        notes.add("Practised on " + daysPractised + " of the last 7 days.");
        if (qNow >= MIN_ANSWERS_PER_WEEK && qBefore >= MIN_ANSWERS_PER_WEEK) {
            int now = pct(thisWeek.stream().mapToInt(DailyPoint::correct).sum(), qNow);
            int before = pct(lastWeek.stream().mapToInt(DailyPoint::correct).sum(), qBefore);
            if (now != before) {
                notes.add("Accuracy over the last 7 days was " + now + "%, "
                        + (now > before ? "up" : "down") + " from " + before + "% the week before.");
            } else {
                notes.add("Accuracy over the last 7 days was " + now + "%, the same as the week before.");
            }
        }

        // Which topics are mentioned is decided on the raw stored mastery; only the
        // number printed in the sentence is the display value.
        List<TopicMasteryOut> practised = topics.stream()
                .filter(t -> rawMastery.containsKey(t.topic()) && t.questionsAnswered() >= MIN_ANSWERS_PER_TOPIC)
                .toList();
        if (!practised.isEmpty()) {
            // Ties go to the topic listed first, as Python's min() and max() do.
            TopicMasteryOut weakest = practised.getFirst();
            TopicMasteryOut strongest = practised.getFirst();
            for (TopicMasteryOut t : practised) {
                if (rawMastery.get(t.topic()) < rawMastery.get(weakest.topic())) {
                    weakest = t;
                }
                if (rawMastery.get(t.topic()) > rawMastery.get(strongest.topic())) {
                    strongest = t;
                }
            }
            if (rawMastery.get(weakest.topic()) < Mastery.EASY_BELOW) {
                notes.add(weakest.label() + " needs the most practice (estimated mastery "
                        + MasteryDisplay.displayText(rawMastery.get(weakest.topic())) + ").");
            }
            if (rawMastery.get(strongest.topic()) > Mastery.HARD_ABOVE) {
                notes.add(strongest.label() + " is a strength (estimated mastery "
                        + MasteryDisplay.displayText(rawMastery.get(strongest.topic())) + ").");
            }
        }

        int hintPct = pct(count(answered, Attempt::isHintUsed), answered.size());
        if (hintPct >= 50) {
            notes.add("A hint was used on " + hintPct + "% of questions.");
        }
        return notes;
    }
}
