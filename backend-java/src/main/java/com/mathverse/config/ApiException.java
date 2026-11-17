package com.mathverse.config;

import java.util.List;

/**
 * An error the API returns on purpose (Python: raise HTTPException).
 * The response body is {"detail": ...}, exactly as FastAPI sends it.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final Object detail;

    /** detail is a message, or for 422 a list of {@link FieldProblem}. */
    public ApiException(int status, Object detail) {
        super(String.valueOf(detail));
        this.status = status;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    public Object detail() {
        return detail;
    }

    /** 401, sent with a WWW-Authenticate: Bearer header by {@link ErrorHandler}. */
    public static ApiException signInRequired() {
        return new ApiException(401, "Please sign in.");
    }

    /** 422 for one bad value, shaped like a FastAPI validation error. */
    public static ApiException invalid(List<Object> loc, String msg, String type) {
        return new ApiException(422, List.of(new FieldProblem(type, loc, msg)));
    }

    /** One entry of a 422 "detail" list. The frontend reads loc and msg. */
    public record FieldProblem(String type, List<Object> loc, String msg) {
    }
}
