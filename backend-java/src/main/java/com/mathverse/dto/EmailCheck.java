package com.mathverse.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The email rules the Python backend applies (Pydantic EmailStr, which uses the
 * email-validator library without a DNS lookup):
 *   - one "@" with a non-empty part before it, at most 64 characters, no ".." and
 *     no dot at either end
 *   - a domain of at least two parts separated by dots, e.g. example.com
 *   - the last part is not all digits, and is not a reserved name such as
 *     "localhost" or "test"
 */
public class EmailCheck implements ConstraintValidator<ValidEmail, String> {

    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?");
    private static final Set<String> RESERVED = Set.of("arpa", "invalid", "local", "localhost", "onion", "test");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isEmail(value);
    }

    static boolean isEmail(String value) {
        int at = value.lastIndexOf('@');
        if (at < 1 || value.length() > 254) {
            return false;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (local.length() > 64 || !LOCAL.matcher(local).matches()
                || local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.length() > 63 || !LABEL.matcher(label).matches()) {
                return false;
            }
        }
        String last = labels[labels.length - 1].toLowerCase();
        return !last.chars().allMatch(Character::isDigit) && !RESERVED.contains(last);
    }
}
