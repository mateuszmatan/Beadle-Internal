package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.DESC;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Component
class CallLogPersistenceAdapter implements CallLogPort {

    private final CallLogJpaRepository entries;
    private final TransactionTemplate ownTransaction;

    CallLogPersistenceAdapter(CallLogJpaRepository entries, PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CallLogEntry record(CallLogEntry entry) {
        return ownTransaction.execute(status -> entries.save(CallLogEntity.of(entry)).toDomain());
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
