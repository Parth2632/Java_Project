package com.mathverse.security;

import com.mathverse.config.ApiException;
import com.mathverse.model.GuardianLinkRepository;
import com.mathverse.model.Roles;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import org.springframework.stereotype.Component;

/**
 * What a signed-in user may see (Python: deps.student_for_viewer). Every
 * endpoint that returns a student's data must go through {@link #studentForViewer}.
 */
@Component
public class Access {

    private final UserRepository users;
    private final GuardianLinkRepository links;

    public Access(UserRepository users, GuardianLinkRepository links) {
        this.users = users;
        this.links = links;
    }

    /**
     * The student whose data is being requested, if the caller may see it: the
     * student themself, or a parent/teacher linked to that student. Anyone else
     * gets 403, also when no such student exists, so ids cannot be probed.
     */
    public User studentForViewer(User viewer, long studentId) {
        ApiException forbidden = new ApiException(403, "You do not have access to this student.");
        if (viewer.getRole().equals(Roles.STUDENT)) {
            if (viewer.getId() != studentId) {
                throw forbidden;
            }
            return viewer;
        }
        if (!links.existsByGuardianIdAndStudentId(viewer.getId(), studentId)) {
            throw forbidden;
        }
        User student = users.findById(studentId).orElse(null);
        if (student == null || !student.getRole().equals(Roles.STUDENT)) {
            throw forbidden;
        }
        return student;
    }
}
