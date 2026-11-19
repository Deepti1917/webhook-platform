package com.webhookplatform.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes error bodies from servlet filters. Messages are fixed strings: no user input is ever echoed. */
public final class JsonErrors {

    private JsonErrors() {}

    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    public static void tooManyRequests(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        write(response, 429, "too_many_requests", "Rate limit exceeded");
    }
}
