package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ProblemDetail revoked(SecurityException e) {
        return problem(HttpStatus.FORBIDDEN, "Pipeline key invalidated", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleData(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", STALE_VERSION);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change violates a database constraint, most likely a duplicate name or key.");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException e) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", e.getMessage());
        detail.setProperty("errors", e.problems());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        logger.error("The portal could not serve a request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed",
                "The portal could not handle the request. The failure is in the portal's log.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        MalformedBody body = MalformedBody.of(e);
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Malformed request", body.detail());
        if (!body.problems().isEmpty()) {
            detail.setProperty("errors", body.problems());
        }
        return handleExceptionInternal(e, detail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail detail = invalid(new InvalidRequestException(e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
                .toList()));
        return handleExceptionInternal(e, detail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The value given for " + nameOf(e) + " is not one this endpoint can read.");
        return handleExceptionInternal(e, detail, headers, HttpStatus.BAD_REQUEST, request);
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

    private static ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        return detail;
    }
}
