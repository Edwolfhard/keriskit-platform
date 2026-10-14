package com.keriskit.error;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(
        basePackages = "com.keriskit",
        annotations = org.springframework.web.bind.annotation.RestController.class
)
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(
            ResponseStatusException exception) {

        String detail = exception.getReason() != null
                ? exception.getReason()
                : "Request could not be completed";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                detail
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(problem);
    }
}