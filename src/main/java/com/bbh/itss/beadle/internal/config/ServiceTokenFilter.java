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
import java.net.URLDecoder;
import java.util.UUID;

import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.CORRELATION_HEADER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.UNKNOWN_USER;
import static com.bbh.itss.beadle.internal.application.calllog.CallerContext.USER_HEADER;
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.CORRELATION_MAX;
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.USER_MAX;
import static com.bbh.itss.beadle.internal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.beadle.internal.domain.shared.Text.bytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

/**
 * Lets through the calls that carry the Beadle service token and runs each one as the caller its headers name:
 * {@code X-Beadle-User} (percent-decoded, then cut to what the call log holds, {@value CallerContext#UNKNOWN_USER}
 * when missing) and {@code X-Correlation-Id} (a fresh one when missing). A correlation id longer than the call log and the followed
 * tickets hold is refused rather than cut, since Beadle-GUI matches the callbacks by it.
 */
@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE)
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String OPEN_PATH = "/actuator/health";
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    public static final String CORRELATION_TOO_LONG = "is too long: it may take at most " + CORRELATION_MAX + " bytes";
    static final String PROBLEM = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
            + DETAIL + "\"}";
    static final String CORRELATION_PROBLEM = "{\"title\":\"Validation failed\",\"status\":400,\"detail\":\""
            + CORRELATION_HEADER + " " + CORRELATION_TOO_LONG + "\",\"problems\":[{\"field\":\"" + CORRELATION_HEADER
            + "\",\"message\":\"" + CORRELATION_TOO_LONG + "\"}]}";

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
        if (!accepts(request.getHeader(AUTHORIZATION))) {
            response.setHeader(WWW_AUTHENTICATE, "Bearer");
            problem(response, UNAUTHORIZED.value(), PROBLEM);
            return;
        }
        String correlationId = trimToNull(request.getHeader(CORRELATION_HEADER));
        if (correlationId != null && bytes(correlationId) > CORRELATION_MAX) {
            problem(response, BAD_REQUEST.value(), CORRELATION_PROBLEM);
            return;
        }
        CallerContext.set(new Caller(userOf(request.getHeader(USER_HEADER)),
                correlationId == null ? UUID.randomUUID().toString() : correlationId));
        try {
            chain.doFilter(request, response);
        } finally {
            CallerContext.clear();
        }
    }

    /**
     * The user {@code X-Beadle-User} names. A header carries ASCII only, so the contract has the name percent-encoded
     * in UTF-8 as {@link java.net.URLEncoder} writes it ({@code Łukasz Wójcik} travels as
     * {@code %C5%81ukasz+W%C3%B3jcik}); a plain ASCII name reads the same either way, and a value that is not valid
     * percent-encoding is taken as it came. The decoded name is trimmed and cut to what the call log holds.
     */
    static String userOf(String header) {
        String user = trimToNull(decoded(trimToNull(header)));
        return user == null ? UNKNOWN_USER : abbreviateBytes(user, USER_MAX);
    }

    private static String decoded(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, UTF_8);
        } catch (IllegalArgumentException notEncoded) {
            return value;
        }
    }

    private static void problem(HttpServletResponse response, int status, String problem) throws IOException {
        response.setStatus(status);
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(problem);
    }

    boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, removeStart(authorization, BEARER).trim().getBytes(UTF_8));
    }
}
