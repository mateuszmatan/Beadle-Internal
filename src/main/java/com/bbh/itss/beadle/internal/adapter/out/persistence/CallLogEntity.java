package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.internal.domain.calllog.Outcome;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.bbh.itss.beadle.internal.adapter.RecordMapper.map;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_INTERNAL_CALL_LOG")
@NoArgsConstructor(access = PROTECTED)
class CallLogEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Instant at;

    private String userName;

    private String correlationId;

    @Enumerated(STRING)
    private ExternalSystem system;

    private String operation;

    private String target;

    @Enumerated(STRING)
    private Outcome outcome;

    private String message;

    private long durationMs;

    static CallLogEntity of(CallLogEntry entry) {
        CallLogEntity entity = new CallLogEntity();
        entity.at = entry.at();
        entity.userName = entry.userName();
        entity.correlationId = entry.correlationId();
        entity.system = entry.system();
        entity.operation = entry.operation();
        entity.target = entry.target();
        entity.outcome = entry.outcome();
        entity.message = entry.message();
        entity.durationMs = entry.durationMs();
        return entity;
    }

    CallLogEntry toDomain() {
        return map(CallLogEntry.class, this);
    }
}
