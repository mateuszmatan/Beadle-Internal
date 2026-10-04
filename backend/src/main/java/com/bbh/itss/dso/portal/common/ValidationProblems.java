package com.bbh.itss.dso.portal.common;

import com.bbh.itss.dso.portal.common.InvalidRequestException.FieldProblem;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the business rule violations of one request so they are all reported together, each against
 * the path of the field it concerns, for example {@code services[2].build.javaPath}.
 */
public class ValidationProblems {

    private final String prefix;
    private final List<FieldProblem> problems;

    public ValidationProblems() {
        this("", new ArrayList<>());
    }

    private ValidationProblems(String prefix, List<FieldProblem> problems) {
        this.prefix = prefix;
        this.problems = problems;
    }

    /** A view that prefixes every field it reports with {@code path}, sharing this collection. */
    public ValidationProblems at(String path) {
        return new ValidationProblems(prefix + path + ".", problems);
    }

    public void add(String field, String message) {
        problems.add(new FieldProblem(prefix + field, message));
    }

    public boolean isEmpty() {
        return problems.isEmpty();
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
