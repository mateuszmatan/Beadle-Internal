# Beadle: the three services and the contracts between them

Beadle is three repositories that work together. Every repository carries this file as `docs/CONTRACTS.md`, the
copies are identical, and the code must match it. [DEPLOYMENT.md](DEPLOYMENT.md), also the same in all three, walks
through running them together and deploying them.

```
                     REST, both ways                      REST (Beadle's service accounts)
 Beadle-GUI  <------------------------------>  Beadle-Internal  --->  Jira, CyberTrack (the Cybersecurity team's
 Angular + Spring Boot, 8081                   Spring Boot, 8082      Jira), Confluence, qTest
 Oracle / H2: system of record, audit trail    Oracle / H2: call log, followed tickets
             <------------------------------>  Beadle-ProTech   <-->  ProTech (BBH's ServiceNow)
                     REST, both ways           Spring Boot, 8083      Table API out, webhook in
                                               Oracle / H2: call log, followed changes
```

## Shared conventions

- Java 21, Spring Boot 4.1.1, Gradle 8.14, Groovy 5 + Spock 2.4, Lombok, Liquibase, JaCoCo, versions in
  `gradle/libs.versions.toml`. Plugins and libraries come from the BBH Nexus only: the services build inside the BBH
  network or on the VPN.
- Profiles: `local` (the default) runs on a BBH laptop with an H2 file database in Oracle mode and the demo adapters,
  and Beadle-GUI signs everybody in as one fixed user. `rd`, `qc` and `prod` (grouped as `oracle`) use Oracle from
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and the real systems only: a service does not start while the address of a
  system it needs is blank.
- Liquibase updates the schema at every start and Hibernate only validates it. A changeset that is on `main` is never
  edited; a change of the schema is a new changeset with a rollback.
- Hexagonal packages: `domain`, `application` (`port/in`, `port/out`), `adapter/in/web`, `adapter/in/schedule`,
  `adapter/out/<system>`, `adapter/out/persistence`, `config`. ArchUnit's `ArchitectureSpec` keeps them apart. Base
  packages `com.bbh.itss.beadle` (Beadle-GUI), `com.bbh.itss.beadle.internal`, `com.bbh.itss.beadle.protech`;
  configuration prefix `beadle.`.
- Names:
  - BBH's ServiceNow instance is **ProTech** in code, configuration, documentation and the interface. The word
    ServiceNow appears only where its REST API is meant.
  - **CyberTrack** is the Cybersecurity team's Jira, where secure coding tickets are created. **Jira** alone is the
    delivery teams' Jira (versions, epics, stories). The two never share a package, a client or a configuration block.
  - **Business Service** is Beadle's grouping of products, one name everywhere: `businessServiceId`,
    `businessServiceName`, `/api/business-services`, the lookup kind `business-services`, the table
    `BEADLE_BUSINESS_SERVICE`, the actions `BUSINESS_SERVICE_*`. ProTech keeps it in its own field `u_department` and
    table `cmn_department`. `directBusinessService` is a different field: ProTech's `business_service` of a change.
- JSON everywhere. Instants are ISO-8601 UTC, dates `yyyy-MM-dd`, enums their Java names.
- Errors are `application/problem+json` (RFC 9457) with `status`, `title` and `detail`: `Validation failed` (400, with
  `problems: [{"field": "...", "message": "..."}]`), `Malformed request` (400, a body or value that cannot be read; in
  Beadle-GUI and Beadle-ProTech), `Not found` (404), `Conflict` (409: a stale `version`, or in Internal and ProTech a
  refusal of the external system, that is a `4xx` other than `401`, `403`, `407`, `408` and `429`), `Bad Gateway` (502,
  Internal or ProTech could not reach the external system, sign in to it or get an answer in time, or it failed),
  `Service Unavailable` (503, Beadle-GUI could not reach Internal or ProTech) and `Request failed` (500). Any other
  error is Spring's own problem, titled with the HTTP reason phrase (`Bad Request`, `Method Not Allowed`, ...).
- Secrets come from environment variables filled from OpenShift secrets, never from a repository, a log or a test
  fixture.
- Logging: SLF4J with Lombok's `@Slf4j` and logback, to the console and to a rolling file `${LOG_PATH}/<app>.log` in
  every profile (`./logs` on a laptop, `/application/logs` in a pod). Every line carries the correlation id
  (`%X{correlationId}`).
- Tests are Spock specifications. `test` holds the unit specs with a JaCoCo gate of 60% of lines. `smokeTest`,
  `regressionTest` and `performanceTest` cover only the key journeys, with a gate of 30% of lines for the three
  together (`jacocoSuitesReport`); smoke and performance take a few tens of seconds, regression at most 2 minutes.
- Each repository ships a `Dockerfile` (UBI 9 OpenJDK 21, user 185, `/application/data`, `/application/logs`) and
  `deploy/openshift/<app>/{base,overlays/{rd,qc,prod}}` (Kustomize). The three services of an environment run in one
  OpenShift project, one pod each (`replicas: 1`), so their schedulers need no lock. Only Beadle-GUI has a Route; it
  reaches the other two inside the project at `http://beadle-internal:8082` and `http://beadle-protech:8083`. The
  secret `beadle-services` holds `BEADLE_SERVICE_TOKEN` for all three. `deploy/docker` in Beadle-GUI runs the three
  together with Docker Compose for testing.

## Calls between the services

Beadle-GUI calls Beadle-Internal and Beadle-ProTech, and both call Beadle-GUI back. Every call carries:

| Header | Value |
|---|---|
| `Authorization` | `Bearer <service token>`: one shared secret per environment, `BEADLE_SERVICE_TOKEN` (`beadle.services.token`) in all three services. A wrong or missing token, or a blank one configured, is answered `401`. |
| `X-Beadle-User` | The Beadle-GUI user who caused the call (`system` for scheduled and callback-triggered calls), percent-encoded UTF-8 as `URLEncoder.encode(name, UTF_8)` writes it (`Łukasz Wójcik` is `%C5%81ukasz+W%C3%B3jcik`). The receiver decodes it with `URLDecoder.decode`, keeps a value that is not valid percent-encoding as it came, and logs `unknown` when the header is missing. Internal and ProTech only log it: they sign in to the external systems with Beadle's generic service accounts. |
| `X-Correlation-Id` | A UUID Beadle-GUI mints per user action, at most 64 bytes of UTF-8; a longer one is answered `400`, and the receiver mints one when it is missing. Internal and ProTech log it and record it in their call log. A callback carries, in this header, the correlation id of the call that made the service follow the ticket or change; no body carries it. |

Only the health endpoints spelled plainly (`/actuator/health`, `/actuator/health/liveness`, ...) are open, for the
OpenShift probes. A path that only looks like one, such as `/actuator/health/..` or `/actuator/health;x`, needs the
token like any other call.

Timeouts are 5 s to connect and 30 s to read. Internal and ProTech give the external systems, and their callbacks to
Beadle-GUI, 5 s to connect and 10 s to read, so that a slow system is answered `502` before Beadle-GUI gives up. An
unreachable service or a `5xx` answer reaches the Beadle-GUI user as `503` with a `detail` naming the service
("Beadle-ProTech did not answer"), and the action is still recorded with outcome `FAILURE`.

Base URLs: in Beadle-GUI `beadle.services.internal.url` (`BEADLE_INTERNAL_URL`, local `http://localhost:8082`) and
`beadle.services.protech.url` (`BEADLE_PROTECH_URL`, local `http://localhost:8083`); in the two services
`beadle.gui.url` (`BEADLE_GUI_URL`, local `http://localhost:8081`).

Both services answer `GET /api/<service>/call-log?correlationId=&from=&to=&page=0&size=50` (`size` 1 to 500) with
`{"page": 0, "size": 50, "total": 12, "entries": [{"id", "at", "userName", "correlationId", "system", "operation",
"target", "outcome", "message", "durationMs"}]}`, newest first: every call they made to an external system or to
Beadle-GUI, for diagnosis.

## Beadle-ProTech REST API (`/api/protech`)

Mirrors Beadle-GUI's `ProTechPort` and `ProTechLookupPort` one to one.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/protech/status` | | `{"connected": false, "mode": "demo"}` or `{"connected": true, "mode": "live"}` |
| `POST /api/protech/changes` | a `Change` (the draft) | `201` `{"number": "CHG0001234", "url": "https://.../CHG0001234" or null}` |
| `POST /api/protech/changes/{number}/tasks` | a `ChangeTask` | `201` `{"number": "CTASK0001234"}` |
| `POST /api/protech/changes/read` | `[Change, ...]` (the changes Beadle-GUI knows) | `{"CHG0001234": Change, ...}`, each as ProTech holds it now; a change ProTech does not have is left out |
| `PUT /api/protech/changes/{number}` | a `Change` | `204` |
| `POST /api/protech/changes/{number}/reminders` | `{"role": "BUSINESS" or null, "task": "CTASK0001234" or null}` | `["Rebecca Lawson", ...]`, the people reminded |
| `GET /api/protech/lookups/{kind}?q=&limit=20` | | `[{"value": "...", "detail": "..."}]`; `kind` is one of `users`, `business-services`, `assignment-groups`, `releases`, `configuration-items`, `incidents`, `problems`, `clients` |
| `POST /api/protech/webhook` | `{"number": "CHG0001234"}` | `202`; for ProTech, see below |

`Change` and `ChangeTask` are the Jackson JSON of Beadle-GUI's records `ProductionChange` and `ChangeTask`
(`domain/change`) with their nested records (`ChangeSchedule`, `ChangeTemplate` with `Approvers`, `Timing`,
`Planning`, `PrivilegedAccess`, `RiskAssessment` and `SecureCoding`; `TaskDetails`, `ChangeApproval`, `WorkflowStep`,
`Reminder`, `ChangeUpdate`) and enums (`ChangeState`, `ApprovalRole`, `ApprovalState`, `TaskState`), by their Java field names.
The business service travels as `businessServiceId` and `businessServiceName` of the change, `template.businessService`
and `ChangeUpdate.businessServiceName`. Beadle-ProTech keeps its own copy of these records; a field is renamed only in
both repositories and this file together. Which ProTech table and field holds each one is in Beadle-ProTech's
`docs/PROTECH-MAPPING.md`, and each is a property under `beadle.protech.fields.*` (for example
`beadle.protech.fields.change.business-service`, default `u_department`).

Every change created or read is followed until it is closed. Beadle-ProTech asks ProTech what moved every
`beadle.protech.poll` (default `PT1M`); the webhook makes it look at one change at once. ProTech calls the webhook from
a business rule with its own token, `PROTECH_WEBHOOK_TOKEN` (`beadle.protech.webhook-token`), which opens that path
alone, so the service token never leaves the OpenShift project. The demo adapter moves its changes on its own clock.

Callback when ProTech moved a change past what Beadle-GUI knows:

`POST {beadle.gui.url}/api/callbacks/protech` with
`{"number": "CHG0001234", "event": "STATE_CHANGED" | "APPROVAL_CHANGED" | "TASK_CHANGED", "state": "IMPLEMENTATION", "at": "2026-10-15T09:00:00Z"}`.
Beadle-GUI answers `202`, reads the change again through `POST /api/protech/changes/read` and records the event as
user `system:protech`; like the two services, it answers `401` without the token and `400` to an `X-Correlation-Id`
longer than 64 bytes. A callback that is not accepted is sent again at the next look.

## Beadle-Internal REST API (`/api/internal`)

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/internal/status` | | `{"jira": {"connected": false, "mode": "demo"}, "cyberTrack": {...}, "confluence": {...}, "qtest": {...}}` |
| `GET /api/internal/jira/projects/{key}/versions` | | `[{"name": "1.4.0", "released": false, "releaseDate": "2026-11-01" or null}]` |
| `GET /api/internal/jira/projects/{key}/epics?fixVersion=` | | `[{"key": "CERT-12", "summary": "...", "status": "In Progress", "epicKey": null, "updated": "2026-10-01"}]` |
| `GET /api/internal/jira/projects/{key}/stories?fixVersion=&epicKeys=CERT-12,CERT-13` | | the same issues, with `epicKey` set |
| `POST /api/internal/cybertrack/tickets` | `{"changeNumber": "CHG0001234", "applicationName": "CertScanner", "implementationDate": "2026-10-20", "inputs": {"apoNumber": "...", "bitbucketUrl": "...", "artifactLink": "...", "qcApplicationLink": "..."}}` | `201` `{"key": "SCP-123", "url": "https://.../browse/SCP-123" or null}` |
| `GET /api/internal/cybertrack/tickets/{key}` | | `{"key": "SCP-123", "status": "To Do", "url": ..., "updated": "2026-10-15T09:00:00Z"}` |
| `GET /api/internal/confluence/pages?spaceKey=&title=` | | `[{"id": "123", "spaceKey": "BEADLE", "title": "...", "version": 3, "url": "..."}]` |
| `GET /api/internal/confluence/pages/{id}` | | the page with `body` (Confluence storage format) |
| `POST /api/internal/confluence/pages` | `{"spaceKey": "BEADLE", "title": "...", "parentId": "123" or null, "body": "<p>...</p>"}` | `201` the page |
| `PUT /api/internal/confluence/pages/{id}` | `{"title": "...", "body": "...", "version": 3}` (the version being replaced) | the page with `version` + 1 |
| `GET /api/internal/qtest/projects` | | `[{"id": 1, "name": "..."}]` |
| `GET /api/internal/qtest/projects/{id}/test-cycles` | | `[{"id": 10, "name": "Release 1.4", "pid": "CL-10"}]` |
| `GET /api/internal/qtest/projects/{id}/test-runs?cycleId=` | | `[{"id": 100, "name": "...", "pid": "TR-100", "status": "Passed", "executedAt": "..." or null}]` |
| `GET /api/internal/qtest/projects/{id}/test-cycles/{cycleId}/summary` | | `{"total": 42, "passed": 40, "failed": 1, "blocked": 0, "unexecuted": 1}` |

Real clients, each behind a port with a demo adapter for the laptop: Jira REST API v2 (`/rest/api/2/...`), CyberTrack
the same API on its own base URL and token (`beadle.cybertrack.url`, `beadle.cybertrack.token`, `project-key` default
`SCP`, `issue-type` default `Task`), Confluence REST API (`/rest/api/content`) and qTest REST API v3
(`/api/v3/projects/...`), all with bearer tokens of Beadle's service accounts.

Callback when a CyberTrack ticket Beadle created changes status (the live adapter reads the open tickets every
`beadle.cybertrack.poll`, default `PT5M`; the demo adapter moves them on its clock):

`POST {beadle.gui.url}/api/callbacks/internal` with `{"system": "CYBERTRACK", "key": "SCP-123", "status": "Done", "at": "..."}`.
Beadle-GUI answers `202` and records it as user `system:internal`. A callback that is not accepted is sent again at
the next poll.

## Audit trail in Beadle-GUI

Beadle-GUI is the system of record and the only place with an audit trail of user actions. Internal and ProTech
keep their own call logs (`BEADLE_INTERNAL_CALL_LOG`, `BEADLE_PROTECH_CALL_LOG`).

Table `BEADLE_ACTION_HISTORY`: `ID`, `AT` (UTC), `USER_NAME`, `ACTION`, `OBJECT_TYPE`, `OBJECT_ID`, `OBJECT_KEY` (the
CHG, CTASK, product code or ticket key a person searches for), `DETAILS` (CLOB, the request as JSON with its
secrets masked; above 256 KB `{"truncated": true, "bytes": n, ...}`), `OUTCOME` (`SUCCESS` or `FAILURE`), `MESSAGE` (the error on failure), `CORRELATION_ID`,
`DURATION_MS`. Rows are only inserted. Indexes on `AT`, `USER_NAME`, `ACTION`, `OBJECT_KEY`.

Every use case that changes something, in Beadle or in another system, records exactly one row, in its transaction
when it has one, and a `FAILURE` row when it throws or its request is refused before it runs (a field problem, a
missing CSRF token of a signed-in user, a callback with the service token but a correlation id that is too long). A
caller who proved nothing, without a session or the service token, or whose sign-in at Ping failed, is only logged,
and a sign-out is recorded only for a signed-in user. Actions: `CHANGE_PREVIEWED`, `CHANGE_CREATED`, `CHANGE_EDITED`,
`CHANGE_TASKS_CREATED`, `SECURE_CODING_TICKET_CREATED`, `REMINDER_SENT`, `CHANGE_SYNCED`, `PRODUCT_CREATED`,
`PRODUCT_UPDATED`, `PRODUCT_DELETED`, `CHANGE_PROFILE_UPDATED`, `BUSINESS_SERVICE_CREATED`, `BUSINESS_SERVICE_RENAMED`,
`BUSINESS_SERVICE_DELETED`, `USER_SIGNED_IN` (the first API call of a session), `USER_SIGNED_OUT`, `CALLBACK_PROTECH`,
`CALLBACK_INTERNAL`. Reads are not recorded, except `CHANGE_PREVIEWED` (a new change checked before it is created)
and `CHANGE_SYNCED` (opening a change pulled a newer state from ProTech).

`GET /api/action-history?user=&action=&objectType=&objectKey=&outcome=&from=&to=&page=0&size=50` answers
`{"content": [...], "page": 0, "size": 50, "total": 1234}`, newest first. `GET /api/action-history/users` and
`GET /api/action-history/actions` answer the values for the filter lists. The Angular page is "Action History" at
`/action-history`.

## Signing in to Beadle-GUI

Users sign in with Ping (OpenID Connect) through Spring Security's OAuth2 login: registration `ping`, client id and
secret from `PING_CLIENT_ID` and `PING_CLIENT_SECRET`, issuer from `PING_ISSUER_URI`, scopes `openid profile email`.
The user's name is the ID token's `name` (then `preferred_username`, then `sub`). Profile `local` has no Ping
(`beadle.auth.mode=local`) and signs everybody in as `beadle.auth.user` (`BEADLE_SIGNED_IN_USER`, default
`Mateusz Matan`); `rd`, `qc` and `prod` use `oidc` and refuse to start in `local`. `/api/**` needs a session, except `/api/callbacks/**` (service token) and
`/actuator/health`. The Angular app sends the browser to `/oauth2/authorization/ping` when an API call answers
`401`. `GET /api/me` answers `{"name": "...", "email": "..." or null}` and `POST /logout` ends the session. The
user's name goes into every audit row and into `X-Beadle-User` of every call to Internal and ProTech; the external
systems never see it.
