--liquibase formatted sql

--changeset beadle-internal:003-cybertrack-ticket-demo dbms:oracle,h2
ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET ADD DEMO NUMBER(1) DEFAULT 0 NOT NULL;
--rollback ALTER TABLE BEADLE_INTERNAL_CYBERTRACK_TICKET DROP COLUMN DEMO;
