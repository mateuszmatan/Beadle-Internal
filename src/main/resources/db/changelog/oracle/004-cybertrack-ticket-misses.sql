--liquibase formatted sql

--changeset beadle-internal:004-cybertrack-ticket-misses dbms:oracle,h2
ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET ADD MISSES NUMBER(5) DEFAULT 0 NOT NULL;
--rollback ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET DROP COLUMN MISSES;
