package com.mathverse.config;

import java.util.List;

/**
 * An error the API returns on purpose (Python: raise HTTPException).
 * The response body is {"detail": ...}, exactly as FastAPI sends it.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final Object detail;
    private final boolean asksForToken;

    /** detail is a message, or for 422 a list of {@link FieldProblem}. */
    public ApiException(int status, Object detail) {
        this(status, detail, false);
    }

    private ApiException(int status, Object detail, boolean asksForToken) {
        super(String.valueOf(detail));
        this.status = status;
        this.detail = detail;
        this.asksForToken = asksForToken;
    }

    public int status() {
        return status;
    }

    public Object detail() {
        return detail;
    }

    /** True when the response should carry a WWW-Authenticate: Bearer header. */
    public boolean asksForToken() {
        return asksForToken;
    }

    /** 401 for a missing or invalid token, sent with a WWW-Authenticate: Bearer header. */
    public static ApiException signInRequired() {
        return new ApiException(401, "Please sign in.", true);
    }

    /** 422 for one bad value, shaped like a FastAPI validation error. */
    public static ApiException invalid(List<Object> loc, String msg, String type) {
        return new ApiException(422, List.of(new FieldProblem(type, loc, msg)));
    }

    /** One entry of a 422 "detail" list. The frontend reads loc and msg. */
    public record FieldProblem(String type, List<Object> loc, String msg) {
    }
}
