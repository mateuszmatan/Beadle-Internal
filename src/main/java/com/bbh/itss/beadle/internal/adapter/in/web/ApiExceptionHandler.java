package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return problem(NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return problem(CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    ProblemDetail unavailable(UncheckedIOException e) {
        return problem(BAD_GATEWAY, "Bad Gateway", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        ProblemDetail detail = problem(BAD_REQUEST, "Validation failed", e.getMessage());
        detail.setProperty("problems", e.problems());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Beadle-Internal could not serve a request", e);
        return problem(INTERNAL_SERVER_ERROR, "Request failed",
                "Beadle-Internal could not handle the request. The failure is in its log.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = forStatusAndDetail(status, message);
        detail.setTitle(title);
        return detail;
    }
}
