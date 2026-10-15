package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.internal.application.calllog.CallerContext;
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort;
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.Caller;
import com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.internal.domain.calllog.Outcome;
import com.bbh.itss.beadle.internal.domain.shared.CallTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.MESSAGE_MAX;
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.TARGET_MAX;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CYBERTRACK;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.GUI;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.JIRA;
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.QTEST;
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE;
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.SUCCESS;
import static com.bbh.itss.beadle.internal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.util.ClassUtils.hasMethod;

@Slf4j
@RequiredArgsConstructor
class CallLogInterceptor implements MethodInterceptor {

    static final String UNLOGGED = "connected";
    static final Map<Class<?>, ExternalSystem> SYSTEMS = Map.of(JiraPort.class, JIRA, CyberTrackPort.class, CYBERTRACK,
            ConfluencePort.class, CONFLUENCE, QTestPort.class, QTEST, CallbackPort.class, GUI);

    private final ObjectProvider<CallLogPort> callLog;
    private final ObjectProvider<Clock> clock;

    static Pointcut pointcut() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return systemOf(method, targetClass).isPresent();
            }
        };
    }

    static Optional<ExternalSystem> systemOf(Method method, Class<?> targetClass) {
        if (UNLOGGED.equals(method.getName())) {
            return Optional.empty();
        }
        return SYSTEMS.entrySet().stream()
                .filter(port -> port.getKey().isAssignableFrom(targetClass)
                        && hasMethod(port.getKey(), method.getName(), method.getParameterTypes()))
                .map(Map.Entry::getValue).findFirst();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis() == null ? method.getDeclaringClass() : invocation.getThis().getClass();
        Optional<ExternalSystem> system = systemOf(method, targetClass);
        if (system.isEmpty()) {
            return invocation.proceed();
        }
        Instant at = now(clock.getIfAvailable(Clock::systemUTC));
        long started = System.nanoTime();
        try {
            Object result = invocation.proceed();
            record(system.get(), method, invocation.getArguments(), at, started, SUCCESS, null);
            return result;
        } catch (Throwable e) {
            record(system.get(), method, invocation.getArguments(), at, started, FAILURE,
                    defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            throw e;
        }
    }

    private void record(ExternalSystem system, Method method, Object[] arguments, Instant at, long started,
                        Outcome outcome, String message) {
        Caller caller = CallerContext.current();
        CallLogEntry entry = new CallLogEntry(null, at, caller.user(), caller.correlationId(), system,
                method.getName(), targetOf(arguments), outcome, abbreviateBytes(message, MESSAGE_MAX),
                (System.nanoTime() - started) / 1_000_000);
        try {
            callLog.getObject().record(entry);
        } catch (RuntimeException e) {
            log.warn("The call of {}.{} on {} could not be written to the call log: {}", system, method.getName(),
                    entry.target(), e.getMessage());
        }
    }

    static String targetOf(Object[] arguments) {
        String target = Arrays.stream(arguments).filter(Objects::nonNull).map(CallLogInterceptor::describe)
                .collect(joining(" "));
        return abbreviateBytes(trimToNull(target), TARGET_MAX);
    }

    private static String describe(Object argument) {
        if (argument instanceof CallTarget target) {
            return target.callTarget();
        }
        if (argument instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(joining(","));
        }
        return String.valueOf(argument);
    }
}
