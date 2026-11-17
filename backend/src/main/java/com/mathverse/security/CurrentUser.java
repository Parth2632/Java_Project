package com.mathverse.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the endpoint parameter that receives the signed-in user, e.g.
 * {@code UserOut me(@CurrentUser User user)}. The user comes only from the
 * bearer token, never from anything the client sends in the body.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
