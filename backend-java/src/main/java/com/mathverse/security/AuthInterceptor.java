package com.mathverse.security;

import com.mathverse.config.ApiException;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Who is calling (Python: deps.current_user and deps.require_role).
 *
 * Runs before every endpoint. For endpoints marked {@link SignedIn} it reads the
 * "Authorization: Bearer <token>" header and finds the user:
 *   no token, a bad or expired token, or a deleted user  → 401 "Please sign in."
 *   signed in, but the wrong role                       → 403 "Your account cannot do this."
 * Otherwise the user is stored on the request for {@link CurrentUserResolver}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String USER_ATTRIBUTE = "mathverse.currentUser";

    private final Tokens tokens;
    private final UserRepository users;

    public AuthInterceptor(Tokens tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        SignedIn rule = method.getMethodAnnotation(SignedIn.class);
        if (rule == null) {
            return true; // a public endpoint
        }
        User user = userFromHeader(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (user == null) {
            throw ApiException.signInRequired();
        }
        if (rule.roles().length > 0 && !Arrays.asList(rule.roles()).contains(user.getRole())) {
            throw new ApiException(403, "Your account cannot do this.");
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        return true;
    }

    private User userFromHeader(String header) {
        if (header == null) {
            return null;
        }
        int space = header.indexOf(' ');
        if (space < 0 || !header.substring(0, space).equalsIgnoreCase("Bearer")) {
            return null;
        }
        String token = header.substring(space + 1);
        if (token.isEmpty()) {
            return null;
        }
        Long userId = tokens.userIdFrom(token);
        return userId == null ? null : users.findById(userId).orElse(null);
    }
}
