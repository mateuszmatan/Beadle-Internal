package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException.FieldProblem;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

import java.util.List;

import static org.apache.commons.lang3.Strings.CS;

record MalformedBody(String detail, List<FieldProblem> problems) {

    private static final String MISSING = "Required request body is missing";
    private static final String UNREADABLE = "has a value this field cannot hold";

    static MalformedBody of(HttpMessageNotReadableException e) {
        if (CS.startsWith(e.getMessage(), MISSING)) {
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
        return new MalformedBody(field + " " + UNREADABLE, List.of(new FieldProblem(field, UNREADABLE)));
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
