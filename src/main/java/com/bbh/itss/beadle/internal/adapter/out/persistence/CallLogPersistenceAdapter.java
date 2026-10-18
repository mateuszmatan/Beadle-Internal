package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.DESC;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

/**
 * The call log: each entry is written in a transaction of its own, so that it stays even when the call it records
 * failed the transaction around it.
 */
@Component
@RequiredArgsConstructor
class CallLogPersistenceAdapter implements CallLogPort {

    private final CallLogJpaRepository entries;

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public CallLogEntry record(CallLogEntry entry) {
        return entries.save(CallLogEntity.of(entry)).toDomain();
    }

    @Override
    public CallLogPage find(CallLogQuery query) {
        Page<CallLogEntity> page = entries.findAll(matching(query),
                PageRequest.of(query.page(), query.size(), Sort.by(DESC, "at", "id")));
        return new CallLogPage(query.page(), query.size(), page.getTotalElements(),
                page.getContent().stream().map(CallLogEntity::toDomain).toList());
    }

    private static Specification<CallLogEntity> matching(CallLogQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.correlationId() != null) {
                predicates.add(builder.equal(root.get("correlationId"), query.correlationId()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("at"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get("at"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
