package com.mathverse.config;

import com.mathverse.config.ApiException.FieldProblem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every error into the body FastAPI would send, so the frontend reads
 * Java errors exactly as it reads Python ones:
 *   {"detail": "message"}                                        most errors
 *   {"detail": [{"type": ..., "loc": [...], "msg": ...}, ...]}   422 validation errors
 * frontend/src/lib/api.ts (describeError) shows "field: msg" from loc and msg.
 */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> apiError(ApiException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status());
        if (e.asksForToken()) { // only "Please sign in.", as in Python; not a wrong password
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(Map.of("detail", e.detail()));
    }

    /** A request body that failed a @NotNull, @Size, ... check. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> invalidBody(MethodArgumentNotValidException e) {
        List<String> order = fieldOrder(e.getBindingResult().getTarget());
        List<FieldError> errors = new ArrayList<>(e.getBindingResult().getFieldErrors());
        // Report in the order the fields are declared, like Pydantic does.
        errors.sort(Comparator.comparingInt(error -> order.indexOf(error.getField().split("\\.")[0])));
        List<FieldProblem> problems = new ArrayList<>();
        for (FieldError error : errors) {
            List<Object> loc = new ArrayList<>();
            loc.add("body");
            for (String part : error.getField().split("\\.")) {
                loc.add(snakeCase(part));
            }
            problems.add(new FieldProblem(pydanticType(error.getCode(), error.getDefaultMessage()), loc,
                    error.getDefaultMessage()));
        }
        return unprocessable(problems);
    }

    /** A body that is not valid JSON, has unknown fields, or has a value of the wrong type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Object> unreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return unprocessable(List.of(new FieldProblem("extra_forbidden",
                    bodyLoc(unknown), "Extra inputs are not permitted")));
        }
        if (cause instanceof MismatchedInputException mismatch) {
            List<Object> loc = bodyLoc(mismatch);
            if (loc.size() == 1) {
                return unprocessable(List.of(new FieldProblem("model_attributes_type", loc,
                        "Input should be a valid dictionary or object to extract fields from")));
            }
            if (String.valueOf(mismatch.getOriginalMessage()).contains("Floating-point")) {
                // e.g. 1.5 for a whole number
                return unprocessable(List.of(new FieldProblem("int_from_float", loc,
                        "Input should be a valid integer, got a number with a fractional part")));
            }
            return unprocessable(List.of(wrongType(loc, mismatch.getTargetType())));
        }
        if (cause instanceof StreamReadException parse) {
            // Where in the text the JSON broke, as Python reports it.
            long offset = 0;
            if (parse.getLocation() != null) {
                offset = Math.max(0, Math.max(parse.getLocation().getCharOffset(), parse.getLocation().getByteOffset()));
            }
            return unprocessable(List.of(new FieldProblem("json_invalid", List.of("body", offset),
                    "JSON decode error")));
        }
        // No body at all.
        return unprocessable(List.of(new FieldProblem("missing", List.of("body"), "Field required")));
    }

    /** The body was sent as something other than JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Object> notJson(HttpMediaTypeNotSupportedException e) {
        return unprocessable(List.of(new FieldProblem("model_attributes_type", List.of("body"),
                "Input should be a valid dictionary or object to extract fields from")));
    }

    /** A path or query value of the wrong type, e.g. /attempts/abc/hint. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> wrongParameterType(MethodArgumentTypeMismatchException e) {
        String where = e.getParameter().hasParameterAnnotation(
                org.springframework.web.bind.annotation.PathVariable.class) ? "path" : "query";
        return unprocessable(List.of(wrongType(List.of(where, snakeCase(e.getName())), e.getRequiredType())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Object> missingQuery(MissingServletRequestParameterException e) {
        return unprocessable(List.of(new FieldProblem("missing", List.of("query", snakeCase(e.getParameterName())),
                "Field required")));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<Object> notFound(Exception e) {
        return ResponseEntity.status(404).body(Map.of("detail", "Not Found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Object> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(Map.of("detail", "Method Not Allowed"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(Map.of("detail", "Internal Server Error"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ResponseEntity<Object> unprocessable(List<FieldProblem> problems) {
        return ResponseEntity.status(422).body(Map.of("detail", problems));
    }

    private static FieldProblem wrongType(List<Object> loc, Class<?> type) {
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return new FieldProblem("int_parsing", loc,
                    "Input should be a valid integer, unable to parse string as an integer");
        }
        if (type == Double.class || type == double.class) {
            return new FieldProblem("float_parsing", loc,
                    "Input should be a valid number, unable to parse string as a number");
        }
        if (type == Boolean.class || type == boolean.class) {
            return new FieldProblem("bool_parsing", loc,
                    "Input should be a valid boolean, unable to interpret input");
        }
        if (type == String.class) {
            return new FieldProblem("string_type", loc, "Input should be a valid string");
        }
        return new FieldProblem("model_attributes_type", loc,
                "Input should be a valid dictionary or object to extract fields from");
    }

    /** ["body", "attention", "face_present_pct"] from the JSON path Jackson reports. */
    private static List<Object> bodyLoc(JacksonException e) {
        List<Object> loc = new ArrayList<>();
        loc.add("body");
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                loc.add(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                loc.add(ref.getIndex());
            }
        }
        return loc;
    }

    /** The record's fields in declaration order (the JSON names, in snake_case). */
    private static List<String> fieldOrder(Object target) {
        List<String> names = new ArrayList<>();
        if (target != null && target.getClass().isRecord()) {
            for (var component : target.getClass().getRecordComponents()) {
                names.add(component.getName());
            }
        }
        return names;
    }

    private static final Pattern CAPITAL = Pattern.compile("([A-Z])");

    static String snakeCase(String javaName) {
        Matcher m = CAPITAL.matcher(javaName);
        return m.replaceAll(match -> "_" + match.group(1).toLowerCase());
    }

    /** The Pydantic error type for each Bean Validation check. */
    private static String pydanticType(String constraint, String message) {
        if (constraint == null) {
            return "value_error";
        }
        return switch (constraint) {
            case "NotNull" -> "missing";
            // Each @Size carries one bound, and its message says which.
            case "Size" -> String.valueOf(message).contains("at least") ? "string_too_short" : "string_too_long";
            case "Pattern" -> "literal_error";
            case "Min", "DecimalMin" -> "greater_than_equal";
            case "Max", "DecimalMax" -> "less_than_equal";
            default -> "value_error";
        };
    }
}
