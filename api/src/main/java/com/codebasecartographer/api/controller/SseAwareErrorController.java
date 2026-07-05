package com.codebasecartographer.api.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Suppresses "Cannot render error page" noise from SSE streams.
 *
 * When an SSE client disconnects, Spring's SseEmitter internally calls
 * completeWithError(ex) which dispatches to /error. Since the HTTP response
 * is already committed (200 OK with text/event-stream), Spring cannot render
 * an error page and logs "Cannot render error page for request [null]".
 *
 * This controller silently absorbs those dispatches when the response is
 * already committed, preventing the log noise that masks real errors.
 */
@Slf4j
@RestController
public class SseAwareErrorController implements ErrorController {

    @RequestMapping("/error")
    public void handleError(HttpServletResponse response) {
        if (response.isCommitted()) {
            // Response already sent (SSE stream) — silently ignore
            log.debug("Suppressing error dispatch for already-committed SSE response");
            return;
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
