package com.bbh.itss.beadle.internal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
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
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(UNAUTHORIZED.value());
        response.setHeader(WWW_AUTHENTICATE, "Bearer");
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(PROBLEM);
    }

    boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, removeStart(authorization, BEARER).trim().getBytes(UTF_8));
    }
}
