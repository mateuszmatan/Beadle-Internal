package com.bbh.itss.beadle.internal.adapter.in.schedule

import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackPollUseCase
import com.bbh.itss.beadle.internal.config.SchedulingConfiguration
import com.bbh.itss.beadle.internal.domain.callback.PollReport
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

    def "a tick polls the tickets and never throws, whatever the poll ran into"() {
        when:
        poller.tick()

        then:
        1 * cyberTrack.poll() >> outcome
        noExceptionThrown()

        where:
        outcome << [PollReport.NOTHING,
                    new PollReport([statusCallback()], ['SCP-1235': 'Beadle-GUI could not take the callback for SCP-1235: 503']),
                    { throw new UncheckedIOException('CyberTrack could not read the tickets SCP-1234: 503', new IOException()) },
                    { throw new IllegalStateException('database down') }]
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
