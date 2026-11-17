package com.mathverse.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicMasteryRepository extends JpaRepository<TopicMastery, Long> {

    Optional<TopicMastery> findByStudentIdAndTopic(Long studentId, String topic);

    List<TopicMastery> findByStudentId(Long studentId);
}
