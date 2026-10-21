package com.example.catalog.adapter.in.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** RFC 9457 problem details with stable type URIs that clients can switch on. */
final class Problems {

    private Problems() {
    }

    static URI type(String slug) {
        return URI.create("urn:catalog:problem:" + slug);
    }

    static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type(slug));
        problem.setTitle(title);
        return problem;
    }

    static ProblemDetail validation(List<FieldViolation> violations) {
        String detail = violations.size() == 1 ? "1 field is invalid" : violations.size() + " fields are invalid";
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, "validation", "Request validation failed", detail);
        problem.setProperty("errors", violations);
        return problem;
    }

    record FieldViolation(String field, String message) {
    }
}
