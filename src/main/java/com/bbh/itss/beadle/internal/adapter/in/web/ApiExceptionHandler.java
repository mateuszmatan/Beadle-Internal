package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        MalformedBody body = MalformedBody.of(e);
        ProblemDetail detail = problem(BAD_REQUEST, "Malformed request", body.detail());
        if (!body.problems().isEmpty()) {
            detail.setProperty("problems", body.problems());
        }
        return handleExceptionInternal(e, detail, headers, BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        ProblemDetail detail = problem(BAD_REQUEST, "Malformed request",
                "The value given for " + nameOf(e) + " is not one this endpoint can read.");
        return handleExceptionInternal(e, detail, headers, BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail detail && detail.getTitle() == null) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            detail.setTitle(resolved == null ? "Request failed" : resolved.getReasonPhrase());
        }
        return response;
    }

    private static String nameOf(TypeMismatchException e) {
        String name = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : e.getPropertyName();
        return name == null ? "a value of this request" : "'" + name + "'";
    }

    static ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = forStatusAndDetail(status, message);
        detail.setTitle(title);
        return detail;
    }
}
