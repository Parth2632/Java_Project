package com.mathverse.service;

import com.mathverse.dto.GameDtos.ProgressOut;
import com.mathverse.model.PlaySessionRepository;
import com.mathverse.model.User;
import org.springframework.stereotype.Service;

/** Levels, unlocks and the progress summary, read from stored missions (Python: the rest of services/game.py). */
@Service
public class Progress {

    private final PlaySessionRepository playSessions;

    public Progress(PlaySessionRepository playSessions) {
        this.playSessions = playSessions;
    }

    public long missionsWon(long studentId) {
        return playSessions.countByStudentIdAndStatus(studentId, "victory");
    }

    /** Level = 1 + number of different bosses defeated (capped at the boss count). */
    public int levelFor(long studentId) {
        long defeated = playSessions.countBossesDefeated(studentId);
        return (int) Math.min(Curriculum.BOSS_IDS.size(), 1 + defeated);
    }

    public ProgressOut progressFor(User student) {
        return new ProgressOut(student.getPoints(), student.getLevel(), Curriculum.unlockedBosses(student.getLevel()),
                missionsWon(student.getId()), student.getLinkCode());
    }
}
