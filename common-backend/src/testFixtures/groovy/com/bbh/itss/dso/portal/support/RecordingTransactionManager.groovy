package com.bbh.itss.dso.portal.support

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus

import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive
import static org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly

class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    final List<String> log = []

    static String transactionState() {
        if (!isActualTransactionActive()) {
            return 'without transaction'
        }
        isCurrentTransactionReadOnly() ? 'read-only' : 'read-write'
    }

    @Override
    protected Object doGetTransaction() {
        new Object()
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        log << (definition.readOnly ? 'begin read-only' : 'begin read-write')
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        log << 'commit'
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        log << 'rollback'
    }
}
