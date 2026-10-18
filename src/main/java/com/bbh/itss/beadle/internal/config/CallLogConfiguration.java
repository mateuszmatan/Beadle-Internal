package com.bbh.itss.beadle.internal.config;

import com.bbh.itss.beadle.internal.adapter.out.calllog.CallLogInterceptor;
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.time.Clock;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

/** Writes every call of the ports to the other systems, the demo ones included, to the call log. */
@Configuration(proxyBeanMethods = false)
public class CallLogConfiguration {

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    static Advisor callLogAdvisor(ObjectProvider<CallLogPort> callLog, ObjectProvider<Clock> clock) {
        return new DefaultPointcutAdvisor(CallLogInterceptor.pointcut(), new CallLogInterceptor(callLog, clock));
    }
}
