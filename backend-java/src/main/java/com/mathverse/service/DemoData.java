package com.mathverse.service;

import com.mathverse.model.GuardianLink;
import com.mathverse.model.GuardianLinkRepository;
import com.mathverse.model.Roles;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import com.mathverse.security.Passwords;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demo accounts (Python: seed.py create_demo_accounts).
 *
 * Demo accounts are marked is_demo = true and can only be reached through
 * POST /auth/demo/{role} when DEMO_MODE=true. Their passwords are random and
 * never shown, so they cannot be used with normal login.
 *
 * Phase 1 creates the accounts and links. The synthetic practice history
 * (three replayed missions) is added in phase 4, with the game rules it needs.
 */
@Service
public class DemoData {

    public static final String EMAIL_DOMAIN = "mathverse.demo";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final GuardianLinkRepository links;
    private final Passwords passwords;

    public DemoData(UserRepository users, GuardianLinkRepository links, Passwords passwords) {
        this.users = users;
        this.links = links;
        this.passwords = passwords;
    }

    /** Creates the demo student, parent and teacher. Returns false if they already exist. */
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
}
