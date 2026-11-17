package com.mathverse.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {

    boolean existsByGuardianIdAndStudentId(Long guardianId, Long studentId);
}
