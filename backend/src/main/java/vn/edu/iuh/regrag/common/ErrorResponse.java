package vn.edu.iuh.regrag.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        List<FieldErrorItem> errors) {

    public record FieldErrorItem(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now(), List.of());
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<FieldErrorItem> errors) {
        return new ErrorResponse(status, error, message, path, Instant.now(), errors);
    }
}
