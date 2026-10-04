package com.bbh.dso.portal.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps exceptions to RFC 9457 problem details. Field level problems are listed under {@code errors}
 * so the UI can show each message next to its input.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleData(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The record was changed by someone else in the meantime. Reload it and apply your change again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change violates a database constraint, most likely a duplicate name or key.");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", e.getMessage());
        detail.setProperty("errors", e.getProblems());
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be read: " + e.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail beanValidation(MethodArgumentNotValidException e) {
        List<InvalidRequestException.FieldProblem> problems = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new InvalidRequestException.FieldProblem(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                problems.size() == 1 ? problems.getFirst().message() : problems.size() + " fields are invalid");
        detail.setProperty("errors", problems);
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        return detail;
    }
}
