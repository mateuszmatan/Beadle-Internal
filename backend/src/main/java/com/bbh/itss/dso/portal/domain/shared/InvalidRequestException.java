package com.bbh.itss.dso.portal.domain.shared;

import lombok.Getter;

import java.util.List;

@Getter
public class InvalidRequestException extends IllegalArgumentException {

    private final List<FieldProblem> problems;

    public InvalidRequestException(List<FieldProblem> problems) {
        super(problems.size() == 1 ? problems.getFirst().message() : problems.size() + " fields are invalid");
        this.problems = List.copyOf(problems);
    }

    public static InvalidRequestException of(String field, String message) {
        return new InvalidRequestException(List.of(new FieldProblem(field, message)));
    }

    public record FieldProblem(String field, String message) {
    }
}
