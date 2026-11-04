package com.springai.web;

import com.springai.governance.GovernanceException;
import com.springai.governance.ModelUnavailableException;
import com.springai.governance.RequestIdFilter;
import com.springai.knowledge.InvalidDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

/** Maps exceptions to problem+json with a stable code and request id. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(GovernanceException.class)
    ResponseEntity<ProblemDetail> governance(GovernanceException e) {
        GovernanceException.Code code = e.getCode();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.detail());
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        if (!e.getCategories().isEmpty()) {
            problem.setProperty("categories", e.getCategories().stream().map(Enum::name).sorted().toList());
        }
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(problem);
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ResponseEntity<ProblemDetail> invalidDocument(InvalidDocumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Document not accepted");
        problem.setProperty("code", "INVALID_DOCUMENT");
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ModelUnavailableException.class)
    ResponseEntity<ProblemDetail> modelUnavailable(ModelUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The language model is not reachable right now. Please try again later.");
        problem.setTitle("Model unavailable");
        problem.setProperty("code", "MODEL_UNAVAILABLE");
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.getRetryAfter().toSeconds())))
                .body(problem);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void securityExceptions(Exception e) throws Exception {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        HttpStatus status;
        String title;
        String detail;
        String code;
        if (hasCause(e, HttpTimeoutException.class, SocketTimeoutException.class)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            title = "Model timed out";
            detail = "The language model did not answer in time. Please try again.";
            code = "MODEL_TIMEOUT";
        } else if (hasCause(e, ConnectException.class, UnknownHostException.class, ResourceAccessException.class)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            title = "Model unavailable";
            detail = "The language model is not reachable right now. Please try again later.";
            code = "MODEL_UNAVAILABLE";
        } else if (hasCause(e, NonTransientAiException.class, TransientAiException.class,
                RestClientResponseException.class)) {
            status = HttpStatus.BAD_GATEWAY;
            title = "Model error";
            detail = "The language model could not process the request.";
            code = "MODEL_ERROR";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            title = "Unexpected error";
            detail = "An unexpected error occurred.";
            code = "INTERNAL_ERROR";
        }
        log.error("request={} failed code={} cause={}", MDC.get(RequestIdFilter.MDC_KEY), code,
                e.getClass().getName(), status == HttpStatus.INTERNAL_SERVER_ERROR ? e : null);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        }
        return response;
    }

    @SafeVarargs
    private static boolean hasCause(Throwable error, Class<? extends Throwable>... types) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
