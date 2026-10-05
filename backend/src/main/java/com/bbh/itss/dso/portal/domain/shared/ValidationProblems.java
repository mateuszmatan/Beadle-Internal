package com.bbh.itss.dso.portal.domain.shared;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;

import java.util.ArrayList;
import java.util.List;

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
