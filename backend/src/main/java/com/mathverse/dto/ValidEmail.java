package com.mathverse.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An email address, checked like Pydantic's EmailStr (see {@link EmailCheck}). Null is left to @NotNull. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailCheck.class)
public @interface ValidEmail {

    String message() default "value is not a valid email address: The email address is not valid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
