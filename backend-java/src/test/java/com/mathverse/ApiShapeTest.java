package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * T5 (server half): there is nowhere to send an image.
 *
 * Port of test_sessions.py::test_api_schema_has_no_image_or_binary_fields. The
 * Python test walks FastAPI's generated /openapi.json. The Java server has no
 * such file, so this test inspects the real endpoints instead: every request
 * and response field (nested ones included), every parameter type, and every
 * accepted content type.
 */
class ApiShapeTest extends ApiTest {

    static final List<String> BANNED = List.of("image", "base64", "binary", "frame", "video", "photo", "picture",
            "multipart", "octet");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void apiHasNoImageOrBinaryFields() {
        List<String> found = new ArrayList<>();
        int endpoints = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            if (!method.getBeanType().getPackageName().startsWith("com.mathverse")) {
                continue; // Spring's own error page
            }
            endpoints++;
            String where = info.toString();
            for (MediaType type : info.getConsumesCondition().getConsumableMediaTypes()) {
                check(type.toString(), where + " consumes", found);
            }
            for (MethodParameter parameter : method.getMethodParameters()) {
                Class<?> type = parameter.getParameterType();
                if (type == byte[].class || MultipartFile.class.isAssignableFrom(type)) {
                    found.add(where + " takes " + type.getSimpleName());
                }
                if (parameter.hasParameterAnnotation(RequestBody.class)) {
                    walk(parameter.getGenericParameterType(), where + " body", found, new HashSet<>());
                }
            }
            walk(method.getMethod().getGenericReturnType(), where + " response", found, new HashSet<>());
        }
        assertThat(endpoints).isGreaterThanOrEqualTo(8); // the check really saw the endpoints
        assertThat(found).isEmpty();
    }

    /** Checks the field names and types of a record, and of records inside it. */
    private static void walk(Type type, String path, List<String> found, Set<Type> seen) {
        if (!seen.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType generic) { // e.g. List<QuestionOut>
            for (Type argument : generic.getActualTypeArguments()) {
                walk(argument, path, found, seen);
            }
            return;
        }
        if (!(type instanceof Class<?> cls)) {
            return;
        }
        if (cls == byte[].class || MultipartFile.class.isAssignableFrom(cls)) {
            found.add(path + " has type " + cls.getSimpleName());
        }
        if (cls.isRecord()) {
            for (RecordComponent component : cls.getRecordComponents()) {
                String name = component.getName();
                check(name, path + "." + name, found);
                walk(component.getGenericType(), path + "." + name, found, seen);
            }
        }
    }

    private static void check(String text, String where, List<String> found) {
        for (String word : BANNED) {
            if (text.toLowerCase().contains(word)) {
                found.add(where + ": " + text);
            }
        }
    }
}
