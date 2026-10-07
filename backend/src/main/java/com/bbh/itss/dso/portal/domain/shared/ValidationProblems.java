package com.bbh.itss.dso.portal.domain.shared;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
public class ValidationProblems {

    private final String prefix;
    private final List<FieldProblem> problems;

    public ValidationProblems() {
        this("", new ArrayList<>());
    }

    public ValidationProblems at(String path) {
        return new ValidationProblems(prefix + path + ".", problems);
    }

    public void add(String field, String message) {
        problems.add(new FieldProblem(prefix + field, message));
    }

    public ValidationProblems require(String field, Object value, String message) {
        if (value == null || value instanceof Collection<?> values && values.isEmpty()) {
            add(field, message);
        }
        return this;
    }

    public List<FieldProblem> list() {
        return List.copyOf(problems);
    }

    public void throwIfAny() {
        if (!problems.isEmpty()) {
            throw new InvalidRequestException(problems);
        }
    }
}
