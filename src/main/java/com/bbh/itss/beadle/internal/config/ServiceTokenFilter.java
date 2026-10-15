package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.domain.calllog.Caller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.CORRELATION_HEADER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.USER_HEADER;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE)
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String OPEN_PATH = "/actuator/health";
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    static final String PROBLEM = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
            + DETAIL + "\"}";

    private final byte[] token;

    public ServiceTokenFilter(ServiceTokenProperties properties) {
        this.token = trimToEmpty(properties.token()).getBytes(UTF_8);
        if (token.length == 0) {
            log.warn("beadle.services.token (BEADLE_SERVICE_TOKEN) is blank: every call of the API is refused");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals(OPEN_PATH) || path.startsWith(OPEN_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (accepts(request.getHeader(AUTHORIZATION))) {
            CallerContext.set(callerOf(request));
            try {
                chain.doFilter(request, response);
            } finally {
                CallerContext.clear();
            }
            return;
        }
        response.setStatus(UNAUTHORIZED.value());
        response.setHeader(WWW_AUTHENTICATE, "Bearer");
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(PROBLEM);
    }

    static Caller callerOf(HttpServletRequest request) {
        String correlationId = trimToNull(request.getHeader(CORRELATION_HEADER));
        return new Caller(trimToNull(request.getHeader(USER_HEADER)),
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }

    boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, removeStart(authorization, BEARER).trim().getBytes(UTF_8));
    }
}
