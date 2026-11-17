package com.mathverse.model;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Database queries for users. Spring writes the SQL from the method names and @Query text. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLinkCode(String linkCode);

    Optional<User> findByLinkCode(String linkCode);

    /** The seeded demo account for a role: lowest id first, demo accounts only. */
    @Query("select u from User u where u.role = ?1 and u.isDemo = true order by u.id asc limit 1")
    Optional<User> findDemoAccount(String role);

    @Query("select count(u) from User u where u.isDemo = true")
    long countDemoAccounts();
}
