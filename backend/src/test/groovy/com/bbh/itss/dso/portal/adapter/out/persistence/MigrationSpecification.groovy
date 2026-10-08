package com.bbh.itss.dso.portal.adapter.out.persistence

import liquibase.Liquibase
import liquibase.Scope
import liquibase.changelog.FastCheckService
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import javax.sql.DataSource

abstract class MigrationSpecification extends Specification {

    @Autowired
    DataSource dataSource

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    PlatformTransactionManager transactionManager

    Liquibase liquibase

    def setup() {
        System.setProperty('liquibase.analytics.enabled', 'false')
        liquibase = new Liquibase('db/changelog/db.changelog-master.yaml', new ClassLoaderResourceAccessor(),
                DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(dataSource.connection)))
    }

    def cleanup() {
        liquibase.close()
    }

    protected int executedSince(String id) {
        jdbc.queryForObject('''SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ORDEREXECUTED >=
                (SELECT MIN(ORDEREXECUTED) FROM DATABASECHANGELOG WHERE ID LIKE ?)''', Integer, id + '%')
    }

    protected void rollBackSince(String id) {
        liquibase.rollback(executedSince(id), '')
        Scope.currentScope.getSingleton(FastCheckService).clearCache()
    }

    protected <T> T inTransaction(Closure<T> work) {
        new TransactionTemplate(transactionManager).execute { work() }
    }

    protected List<String> tables() {
        jdbc.queryForList('SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES', String)
    }

    protected List<String> columns(String table) {
        jdbc.queryForList('SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?', String, table)
    }

    protected boolean nullable(String table, String column) {
        jdbc.queryForObject('SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?',
                String, table, column) == 'YES'
    }
}
