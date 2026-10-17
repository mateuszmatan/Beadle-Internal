package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.internal.domain.calllog.Outcome;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_INTERNAL_CALL_LOG")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
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
        return new CallLogEntity(null, entry.at(), entry.userName(), entry.correlationId(), entry.system(),
                entry.operation(), entry.target(), entry.outcome(), entry.message(), entry.durationMs());
    }

    CallLogEntry toDomain() {
        return new CallLogEntry(id, at, userName, correlationId, system, operation, target, outcome, message,
                durationMs);
    }
}
