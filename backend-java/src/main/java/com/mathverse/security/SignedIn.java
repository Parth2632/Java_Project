package com.mathverse.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on an endpoint that needs a signed-in caller (Python: Depends(current_user)).
 * With roles, only those roles may call it (Python: require_role):
 * <pre>
 *   &#64;SignedIn                                  any signed-in user
 *   &#64;SignedIn(roles = "student")               students only       (require_student)
 *   &#64;SignedIn(roles = {"parent", "teacher"})   parents and teachers (require_guardian)
 * </pre>
 * {@link AuthInterceptor} enforces it before the request body is even read.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SignedIn {

    String[] roles() default {};
}
