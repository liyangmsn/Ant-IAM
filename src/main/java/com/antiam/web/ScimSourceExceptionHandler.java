package com.antiam.web;

import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.common.ScimException;
import com.antiam.dto.ScimSourceDtos.ScimError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 身份源 SCIM 端点的错误统一按 RFC 7644 §3.12 的 SCIM Error 格式返回，优先于全局的 ProblemDetail 处理器。
 */
@RestControllerAdvice(assignableTypes = ScimSourceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScimSourceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ScimSourceExceptionHandler.class);
    private static final MediaType SCIM_JSON = MediaType.parseMediaType("application/scim+json");

    @ExceptionHandler(ScimException.class)
    ResponseEntity<ScimError> scim(ScimException ex) {
        return error(ex.status(), ex.scimType(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ScimError> validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .findFirst()
            .orElse("Request validation failed");
        return error(400, "invalidValue", detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ScimError> unreadable(Exception ex) {
        return error(400, "invalidSyntax", "Request body or parameter is malformed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ScimError> illegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
        return error(400, message.toLowerCase().contains("filter") ? "invalidFilter" : "invalidValue", message);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ScimError> conflict(ConflictException ex) {
        return error(409, "uniqueness", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ScimError> notFound(NotFoundException ex) {
        return error(404, null, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ScimError> integrity(DataIntegrityViolationException ex) {
        log.warn("SCIM data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error(409, "uniqueness", "Resource conflicts with existing data");
    }

    private static ResponseEntity<ScimError> error(int status, String scimType, String detail) {
        return ResponseEntity.status(status).contentType(SCIM_JSON).body(ScimError.of(status, scimType, detail));
    }
}
