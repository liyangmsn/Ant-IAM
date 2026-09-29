package com.antiam.common;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ProblemDetail applicationAccessDenied(ApplicationAccessDeniedException ex) {
        ProblemDetail detail = problem(HttpStatus.FORBIDDEN, "application-access-denied", ex.getMessage());
        detail.setProperty("reason", ex.reason());
        return detail;
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "Request conflicts with existing data or references");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "validation", message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail constraintViolation(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Request validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid value for parameter: " + ex.getName());
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MissingRequestHeaderException.class,
        MultipartException.class
    })
    ProblemDetail badRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", ex.getMessage());
    }

    /**
     * OAuth2 协议端点错误保持 RFC 6749 的 error / error_description 字段，同时保留 ProblemDetail 字段便于控制台展示。
     */
    @ExceptionHandler(OAuthException.class)
    ResponseEntity<Map<String, Object>> oauth(OAuthException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.error());
        body.put("error_description", ex.getMessage());
        body.put("type", "urn:ant-iam:error:oauth-" + ex.error().replace('_', '-'));
        body.put("status", ex.status().value());
        body.put("detail", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        if (ex.status() == HttpStatus.UNAUTHORIZED) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "invalid_token".equals(ex.error())
                ? "Bearer error=\"invalid_token\""
                : "Basic realm=\"ant-iam\"");
        }
        return ResponseEntity.status(ex.status()).headers(headers).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail serviceUnavailable(ServiceUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", ex.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail upstream(RestClientException ex) {
        log.warn("Upstream request failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "upstream", "Upstream service request failed");
    }

    /**
     * 业务代码用 IllegalStateException 表达"外部依赖失败或未就绪"，按网关错误返回，避免 500。
     */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail illegalState(IllegalStateException ex) {
        log.warn("Request failed due to service state", ex);
        return problem(HttpStatus.BAD_GATEWAY, "upstream", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("urn:ant-iam:error:" + type));
        return detail;
    }
}
