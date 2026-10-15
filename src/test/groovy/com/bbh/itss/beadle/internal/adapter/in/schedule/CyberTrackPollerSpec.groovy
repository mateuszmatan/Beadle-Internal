package com.bbh.itss.beadle.internal.adapter.in.schedule

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackPollUseCase
import com.bbh.itss.beadle.internal.config.SchedulingConfiguration
import com.bbh.itss.beadle.internal.domain.callback.PollReport
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import spock.lang.Specification

import java.time.Duration
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.support.Fixtures.statusCallback

class CyberTrackPollerSpec extends Specification {

    CyberTrackPollUseCase cyberTrack = Mock()
    def poller = new CyberTrackPoller(cyberTrack)
    def logged = new ListAppender<ILoggingEvent>()
    def logger = LoggerFactory.getLogger(CyberTrackPoller) as Logger
    def formerLevel = logger.level

    def setup() {
        logged.start()
        logger.addAppender(logged)
        logger.level = Level.INFO
    }

    def cleanup() {
        logger.detachAppender(logged)
        logger.level = formerLevel
    }

    def "a tick polls the tickets and logs the callbacks it sent and the tickets it could not follow up"() {
        when:
        poller.tick()

        then:
        1 * cyberTrack.poll() >> report
        noExceptionThrown()
        logged.list.collect { "$it.level $it.formattedMessage" as String } == messages

        where:
        report << [PollReport.NOTHING,
                   new PollReport([statusCallback()], ['SCP-1235': 'Beadle-GUI could not take the callback for SCP-1235: 503'])]
        messages << [[],
                     ['INFO Told Beadle-GUI that CyberTrack ticket SCP-1234 is now In Progress',
                      'WARN CyberTrack ticket SCP-1235 could not be followed up and is tried again on the next tick: ' +
                              'Beadle-GUI could not take the callback for SCP-1235: 503']]
    }

    def "a tick never throws the #failure.class.simpleName the poll ran into, it logs it and tries again on the next tick"() {
        when:
        poller.tick()

        then:
        1 * cyberTrack.poll() >> { throw failure }
        noExceptionThrown()
        logged.list.size() == 1
        with(logged.list.first()) {
            level == Level.WARN
            formattedMessage == 'The CyberTrack poll failed and is tried again on the next tick: ' + failure.message
            throwableProxy.className == failure.class.name
        }

        where:
        failure << [new UncheckedIOException('CyberTrack could not read the tickets SCP-1234: 503', new IOException()),
                    new IllegalStateException('database down')]
    }

    def "the tickets are polled every #expected #mode"() {
        given:
        def runner = new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfiguration, CyberTrackPoller)
                .withBean(CyberTrackPollUseCase, { [poll: { PollReport.NOTHING }] as CyberTrackPollUseCase } as Supplier<CyberTrackPollUseCase>)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            def tasks = context.getBeansOfType(ScheduledTaskHolder).values()*.scheduledTasks.flatten()
            assert tasks.size() == 1
            def task = tasks.first().task as FixedDelayTask
            assert task.intervalDuration == expected
            assert task.initialDelayDuration == expected
        }

        where:
        mode                                      | properties                                                                       || expected
        'on the demo clock'                       | []                                                                               || Duration.ofSeconds(10)
        'on the demo clock, whatever the poll is' | ['beadle.cybertrack.url=', 'beadle.cybertrack.poll=PT1M']                        || Duration.ofSeconds(10)
        'by default once CyberTrack is set'       | ['beadle.cybertrack.url=https://cybertrack.bbh.com']                             || Duration.ofMinutes(5)
        'as configured once CyberTrack is set'    | ['beadle.cybertrack.url=https://cybertrack.bbh.com', 'beadle.cybertrack.poll=PT1M'] || Duration.ofMinutes(1)
    }
}
