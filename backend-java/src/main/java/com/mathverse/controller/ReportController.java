package com.mathverse.controller;

import com.mathverse.config.ApiException;
import com.mathverse.dto.ReportDtos.StudentReport;
import com.mathverse.model.User;
import com.mathverse.security.Access;
import com.mathverse.security.CurrentUser;
import com.mathverse.security.SignedIn;
import com.mathverse.service.ReportBuilder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Student report, for the student themself or a linked parent/teacher (Python: routers/reports.py). */
@RestController
public class ReportController {

    private final Access access;
    private final ReportBuilder reports;

    public ReportController(Access access, ReportBuilder reports) {
        this.access = access;
        this.reports = reports;
    }

    /**
     * tz_offset_minutes is the viewer's clock offset from UTC (-840 to 840), so
     * daily trends follow their calendar day. It is read as text and checked
     * after the access check, as FastAPI does.
     */
    @GetMapping("/students/{studentId}/report")
    @SignedIn
    public StudentReport report(@CurrentUser User viewer, @PathVariable long studentId,
                                @RequestParam(name = "tz_offset_minutes", defaultValue = "0") String tzOffsetMinutes) {
        User student = access.studentForViewer(viewer, studentId);
        return reports.build(student, parseOffset(tzOffsetMinutes));
    }

    private static int parseOffset(String text) {
        List<Object> loc = List.of("query", "tz_offset_minutes");
        int offset;
        try {
            offset = Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw ApiException.invalid(loc, "Input should be a valid integer, unable to parse string as an integer",
                    "int_parsing");
        }
        if (offset < -840) {
            throw ApiException.invalid(loc, "Input should be greater than or equal to -840", "greater_than_equal");
        }
        if (offset > 840) {
            throw ApiException.invalid(loc, "Input should be less than or equal to 840", "less_than_equal");
        }
        return offset;
    }
}
