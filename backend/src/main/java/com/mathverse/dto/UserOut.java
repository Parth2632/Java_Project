package com.mathverse.dto;

import com.mathverse.model.User;

/** An account as the frontend sees it. The password hash is never included. */
public record UserOut(
        long id,
        String name,
        String email,
        String role,
        int points,
        int level,
        String linkCode, // null for parents and teachers
        boolean isDemo) {

    public static UserOut from(User user) {
        return new UserOut(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getPoints(), user.getLevel(), user.getLinkCode(), user.isDemo());
    }
}
