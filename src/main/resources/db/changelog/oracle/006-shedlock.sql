--liquibase formatted sql

--changeset beadle-internal:006-shedlock dbms:oracle,h2
CREATE TABLE BEADLE_INTERNAL_SHEDLOCK (
    NAME                        VARCHAR2(64)    NOT NULL,
    LOCK_UNTIL                  TIMESTAMP       NOT NULL,
    LOCKED_AT                   TIMESTAMP       NOT NULL,
    LOCKED_BY                   VARCHAR2(255)   NOT NULL,
    CONSTRAINT PK_BEADLE_INTERNAL_SHEDLOCK PRIMARY KEY (NAME)
);
--rollback DROP TABLE BEADLE_INTERNAL_SHEDLOCK;
