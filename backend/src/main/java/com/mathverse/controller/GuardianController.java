package com.mathverse.controller;

import com.mathverse.dto.ReportDtos.LinkRequest;
import com.mathverse.dto.ReportDtos.LinkedStudent;
import com.mathverse.model.User;
import com.mathverse.security.CurrentUser;
import com.mathverse.security.SignedIn;
import com.mathverse.service.Guardians;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Parent and teacher endpoints (Python: routers/guardians.py). */
@RestController
@RequestMapping("/guardians")
public class GuardianController {

    private final Guardians guardians;

    public GuardianController(Guardians guardians) {
        this.guardians = guardians;
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    @SignedIn(roles = {"parent", "teacher"})
    public LinkedStudent link(@CurrentUser User guardian, @Valid @RequestBody LinkRequest body) {
        return guardians.link(guardian, body.linkCode());
    }

    @GetMapping("/students")
    @SignedIn(roles = {"parent", "teacher"})
    public List<LinkedStudent> myStudents(@CurrentUser User guardian) {
        return guardians.myStudents(guardian);
    }
}
