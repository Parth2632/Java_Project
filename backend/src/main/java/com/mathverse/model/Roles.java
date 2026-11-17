package com.mathverse.model;

import java.util.List;

/** The three account roles (Python: schemas.Role). Matching is exact: "Student" is not a role. */
public final class Roles {

    public static final String STUDENT = "student";
    public static final String PARENT = "parent";
    public static final String TEACHER = "teacher";
    public static final List<String> ALL = List.of(STUDENT, PARENT, TEACHER);

    /** For 422 messages, worded as Pydantic words them. */
    public static final String INVALID_MESSAGE = "Input should be 'student', 'parent' or 'teacher'";

    private Roles() {
    }
}
