package com.pradeep.finance.assistant;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Exposes safe, intentional Assistant errors to the Angular client as RFC 9457 details. */
@RestControllerAdvice(assignableTypes = AssistantController.class)
public class AssistantExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException exception) {
        String detail = exception.getReason() == null || exception.getReason().isBlank()
                ? "The Assistant could not complete that request. Please try again."
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail));
    }
}
