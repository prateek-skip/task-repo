package com.example.catalog.adapter.in.web;

import com.example.catalog.adapter.in.web.Problems.FieldViolation;
import com.example.catalog.domain.exception.CategoryNotFoundException;
import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.exception.InvalidCategoryReferenceException;
import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.exception.VersionMismatchException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place that maps failures to HTTP. Every error is an RFC 9457 problem with a stable
 * type, so clients branch on {@code type} rather than parsing messages. The base class already
 * covers framework errors such as malformed JSON (400) and unsupported media types (415).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ProblemDetail> handleDomainValidation(DomainValidationException e) {
        return respond(Problems.validation(List.of(new FieldViolation(e.field(), e.getMessage()))));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ProblemDetail> handleInvalidCursor(InvalidCursorException e) {
        return respond(Problems.of(HttpStatus.BAD_REQUEST, "invalid-cursor", "Invalid cursor", e.getMessage()));
    }

    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e) {
        return respond(Problems.of(HttpStatus.NOT_FOUND, "not-found", "Resource not found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ResponseEntity<ProblemDetail> handleDuplicateSku(DuplicateSkuException e) {
        return respond(Problems.of(HttpStatus.CONFLICT, "duplicate-sku", "SKU already in use", e.getMessage()));
    }

    @ExceptionHandler(ConcurrentUpdateException.class)
    ResponseEntity<ProblemDetail> handleConcurrentUpdate(ConcurrentUpdateException e) {
        return respond(Problems.of(HttpStatus.CONFLICT, "concurrent-update", "Concurrent update", e.getMessage()));
    }

    @ExceptionHandler(VersionMismatchException.class)
    ResponseEntity<ProblemDetail> handleVersionMismatch(VersionMismatchException e) {
        ProblemDetail problem = Problems.of(HttpStatus.PRECONDITION_FAILED, "version-mismatch", "Precondition failed", e.getMessage());
        problem.setProperty("currentVersion", e.currentVersion());
        return respond(problem);
    }

    // Well-formed request for a valid resource that cannot be processed: 422, not 404, because
    // the thing that is missing is referenced by the body, not addressed by the URL.
    @ExceptionHandler(InvalidCategoryReferenceException.class)
    ResponseEntity<ProblemDetail> handleUnknownCategory(InvalidCategoryReferenceException e) {
        ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "unknown-category", "Unknown category", e.getMessage());
        problem.setProperty("errors", List.of(new FieldViolation("category", e.getMessage())));
        return respond(problem);
    }

    // The database is unreachable or overloaded. 503 with Retry-After tells clients and load
    // balancers this is temporary, unlike a 500.
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class, TransientDataAccessException.class})
    ResponseEntity<ProblemDetail> handleUnavailable(RuntimeException e) {
        log.warn("Database unavailable: {}", e.toString());
        ProblemDetail problem = Problems.of(HttpStatus.SERVICE_UNAVAILABLE, "unavailable", "Service temporarily unavailable",
                "The catalog is temporarily unavailable; retry shortly");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").body(problem);
    }

    // Unexpected errors are logged in full but never leak internals to the client.
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled error", e);
        return respond(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal error", "An unexpected error occurred"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = e.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Problems.validation(violations));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .map(message -> new FieldViolation(result.getMethodParameter().getParameterName(), message)))
                .toList();
        return ResponseEntity.badRequest().body(Problems.validation(violations));
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
