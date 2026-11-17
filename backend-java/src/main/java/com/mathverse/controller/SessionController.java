package com.mathverse.controller;

import com.mathverse.dto.GameDtos.AnswerRequest;
import com.mathverse.dto.GameDtos.AnswerResponse;
import com.mathverse.dto.GameDtos.HintOut;
import com.mathverse.dto.GameDtos.ProgressOut;
import com.mathverse.dto.GameDtos.StartSessionRequest;
import com.mathverse.dto.GameDtos.StartSessionResponse;
import com.mathverse.model.User;
import com.mathverse.security.CurrentUser;
import com.mathverse.security.SignedIn;
import com.mathverse.service.Missions;
import com.mathverse.service.Progress;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gameplay endpoints, students only (Python: routers/sessions.py). */
@RestController
public class SessionController {

    private final Missions missions;
    private final Progress progress;

    public SessionController(Missions missions, Progress progress) {
        this.missions = missions;
        this.progress = progress;
    }

    @GetMapping("/students/me/progress")
    @SignedIn(roles = "student")
    public ProgressOut myProgress(@CurrentUser User student) {
        return progress.progressFor(student);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @SignedIn(roles = "student")
    public StartSessionResponse start(@CurrentUser User student, @Valid @RequestBody StartSessionRequest body) {
        return missions.start(student, body.bossId());
    }

    @GetMapping("/attempts/{attemptId}/hint")
    @SignedIn(roles = "student")
    public HintOut hint(@CurrentUser User student, @PathVariable long attemptId) {
        return missions.hint(student, attemptId);
    }

    @PostMapping("/attempts/{attemptId}/answer")
    @SignedIn(roles = "student")
    public AnswerResponse answer(@CurrentUser User student, @PathVariable long attemptId,
                                 @Valid @RequestBody AnswerRequest body) {
        return missions.answer(student, attemptId, body);
    }
}
