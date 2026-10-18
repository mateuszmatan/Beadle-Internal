package com.bbh.itss.beadle.internal.application.calllog;

import com.bbh.itss.beadle.internal.domain.calllog.Caller;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * Who caused the work of the current thread: the user and correlation id of the call from Beadle-GUI, or
 * {@code system} for the poll. The correlation id is also in the logging MDC as {@code correlationId}.
 */
@NoArgsConstructor(access = PRIVATE)
public final class CallerContext {

    public static final String USER_HEADER = "X-Beadle-User";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String SYSTEM_USER = "system";
    public static final String UNKNOWN_USER = "unknown";
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    public static Caller current() {
        return getIfNull(CURRENT.get(), Caller.NONE);
    }

    /**
     * Makes {@code caller} the current one until the scope is closed, then the one before.
     */
    public static Scope open(Caller caller) {
        Caller before = CURRENT.get();
        set(caller);
        return () -> set(before);
    }

    public static void run(Caller caller, Runnable work) {
        try (Scope scope = open(caller)) {
            work.run();
        }
    }

    public static <T> T as(Caller caller, Supplier<T> work) {
        try (Scope scope = open(caller)) {
            return work.get();
        }
    }

    private static void set(Caller caller) {
        if (caller == null) {
            CURRENT.remove();
            MDC.remove(MDC_KEY);
        } else {
            CURRENT.set(caller);
            MDC.put(MDC_KEY, caller.correlationId());
        }
    }

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
