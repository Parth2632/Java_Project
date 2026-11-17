package com.mathverse.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaySessionRepository extends JpaRepository<PlaySession, Long> {

    List<PlaySession> findByStudentIdAndStatus(Long studentId, String status);

    long countByStudentIdAndStatus(Long studentId, String status);

    /** How many different bosses this student has beaten. */
    @Query("select count(distinct s.bossId) from PlaySession s where s.studentId = ?1 and s.status = 'victory'")
    long countBossesDefeated(Long studentId);
}
