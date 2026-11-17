package com.mathverse.service;

import com.mathverse.config.ApiException;
import com.mathverse.dto.ReportDtos.LinkedStudent;
import com.mathverse.model.GuardianLink;
import com.mathverse.model.GuardianLinkRepository;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parents and teachers linking to the students they are allowed to view (Python: routers/guardians.py). */
@Service
public class Guardians {

    private final UserRepository users;
    private final GuardianLinkRepository links;
    private final ReportBuilder reports;

    public Guardians(UserRepository users, GuardianLinkRepository links, ReportBuilder reports) {
        this.users = users;
        this.links = links;
        this.reports = reports;
    }

    /**
     * Links the caller to a student using the student's link code. The code is
     * shown only inside the student's own account, so having it means the child
     * (or their family) chose to share it. Linking twice is harmless.
     */
    @Transactional
    public LinkedStudent link(User guardian, String linkCode) {
        String code = linkCode.strip().toUpperCase(Locale.ROOT);
        User student = users.findStudentByLinkCode(code).orElse(null);
        // Demo and real accounts are kept apart in both directions. Answer exactly
        // as if the code did not exist, so nothing is revealed either way.
        if (student == null || student.isDemo() != guardian.isDemo()) {
            throw new ApiException(404, "No student found with that code.");
        }
        if (!links.existsByGuardianIdAndStudentId(guardian.getId(), student.getId())) {
            links.save(new GuardianLink(guardian.getId(), student.getId()));
        }
        return reports.summaryForList(student);
    }

    @Transactional(readOnly = true)
    public List<LinkedStudent> myStudents(User guardian) {
        return users.findLinkedStudents(guardian.getId(), guardian.isDemo()).stream()
                .map(reports::summaryForList)
                .toList();
    }
}
