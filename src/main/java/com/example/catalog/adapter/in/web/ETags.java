package com.example.catalog.adapter.in.web;

import java.util.OptionalLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The product version is the entity tag. It changes on every write and only on a write, so a
 * match means the client holds exactly the current representation.
 */
final class ETags {

    private ETags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * The version a client expects, from If-Match. Absent or "*" means no precondition. A tag that
     * is not one of ours can never match the current version, so it fails the precondition.
     */
    static OptionalLong expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            return OptionalLong.empty();
        }
        String tag = ifMatch.strip();
        if (tag.length() > 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
            try {
                return OptionalLong.of(Long.parseLong(tag.substring(1, tag.length() - 1)));
            } catch (NumberFormatException ignored) {
                // fall through to the precondition failure below
            }
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "If-Match must be a single strong entity tag returned by this API, such as \"3\"");
        problem.setType(Problems.type("version-mismatch"));
        problem.setTitle("Precondition failed");
        throw new ErrorResponseException(HttpStatus.PRECONDITION_FAILED, problem, null);
    }
}
