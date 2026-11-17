package com.mathverse.model;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByStudentIdOrderById(Long studentId);

    /** A student's answered questions, newest first. */
    @Query("select a from Attempt a where a.studentId = ?1 and a.answeredAt is not null "
            + "order by a.answeredAt desc, a.id desc")
    List<Attempt> findAnswered(Long studentId);
}
