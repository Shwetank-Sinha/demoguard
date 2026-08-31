package io.demoguard.dashboard.api;

import io.demoguard.dashboard.api.DashboardDtos.ApiError;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> invalid(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", safeMessage(exception, "The request is invalid"));
    }

    @ExceptionHandler(KubernetesClientException.class)
    ResponseEntity<ApiError> kubernetes(KubernetesClientException exception) {
        if (exception.getCode() == 403) {
            return error(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "The dashboard identity does not have permission to perform this Kubernetes API request");
        }
        if (exception.getCode() == 404) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested Kubernetes resource was not found");
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, "KUBERNETES_API_UNAVAILABLE",
                "The Kubernetes API is unavailable; verify the cluster connection and try again");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ignored) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The dashboard could not complete the request");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now()));
    }

    private static String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
