package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

record MalformedBody(String detail, List<FieldProblem> problems) {

    private static final String MISSING = "Required request body is missing";

    static MalformedBody of(HttpMessageNotReadableException e) {
        if (e.getMessage() != null && e.getMessage().startsWith(MISSING)) {
            return new MalformedBody("The request body is missing.", List.of());
        }
        if (e.getCause() instanceof DatabindException databind) {
            return ofValue(databind);
        }
        if (e.getCause() instanceof JacksonException) {
            return new MalformedBody("The request body is not valid JSON.", List.of());
        }
        return new MalformedBody("The request body could not be read.", List.of());
    }

    private static MalformedBody ofValue(DatabindException e) {
        String field = fieldOf(e);
        if (field.isEmpty()) {
            return new MalformedBody("The request body does not have the shape this endpoint expects.", List.of());
        }
        String reason = reasonOf(e);
        return new MalformedBody(field + " " + reason, List.of(new FieldProblem(field, reason)));
    }

    private static String reasonOf(DatabindException e) {
        if (e instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            return "must be one of " + Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(String::valueOf).collect(Collectors.joining(", "));
        }
        return "has a value this field cannot hold";
    }

    private static String fieldOf(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
