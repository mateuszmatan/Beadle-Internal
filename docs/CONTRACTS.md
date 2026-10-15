# Beadle: the three repositories and the contracts between them

Source of everything: `mateuszmatan/DSOMgmtPortal` @ `278766b` (2026-10-15). Three repositories are cut out of it,
each with the git history of its files. This document is the shared contract; every repository carries a copy in
`docs/CONTRACTS.md` and the code must match it. Code, comments, commit messages and documentation are in English.

```
                     REST, both ways                      REST (service accounts of Beadle)
 Beadle-GUI  <------------------------------>  Beadle-Internal  --->  BBH Jira, CyberTrack (the Jira of the
 Angular + Java (Spring Boot)                  Java (Spring Boot)     Cybersecurity team), Confluence, qTest
 Oracle / H2: system of record + audit trail   Oracle / H2: call log
             <------------------------------>  Beadle-ProTech   --->  ProTech (ServiceNow of BBH)
                     REST, both ways           Java (Spring Boot)
                                               Oracle / H2: call log, demo state
```

## Conventions shared by the three repositories

- Java 21, Spring Boot 4.1.1 and the Gradle 8.14 wrapper, Groovy 5 + Spock 2.4 tests, Lombok, Liquibase, Jacoco,
  exactly as in `gradle/libs.versions.toml` and `settings.gradle` of DSOMgmtPortal (BBH Nexus when reachable,
  Gradle Plugin Portal + Maven Central + registry.npmjs.org otherwise; keep that logic as it is).
- Spring profiles: `local` (default: H2 file database in Oracle mode, demo adapters, test user) and `oracle`,
  grouped into `rd`, `qc`, `prod` (Oracle from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; real adapters).
  Liquibase runs on every start with `ddl-auto: validate`.
- Hexagonal layout and naming as in DSOMgmtPortal: `adapter/in/web`, `adapter/out/<system>`,
  `application/<area>` with `port/in` and `port/out`, `domain/<area>`, `config`. ArchUnit `ArchitectureSpec` keeps
  the layers apart.
- Base packages: `com.bbh.itss.beadle` (Beadle-GUI backend), `com.bbh.itss.beadle.internal` (Beadle-Internal),
  `com.bbh.itss.beadle.protech` (Beadle-ProTech). Configuration prefix `beadle.` (was `dso.`).
- Names: the ServiceNow instance of BBH is called **ProTech** everywhere in code, configuration, documentation and
  the interface (`ProTechPort`, `adapter/out/protech`, `beadle.protech.*`, `proTechConnected`). The word
  ServiceNow appears only where the product or its REST API is meant (for example in the comments of the real
  adapter and the Table API URLs). **CyberTrack** is the Jira of the Cybersecurity team where secure coding
  tickets are raised; **Jira** alone means the BBH Jira of the delivery teams (versions, epics, stories). The two
  never share a package, a client or a configuration block.
- Every REST API answers JSON. Errors are `application/problem+json` (RFC 9457): `status`, `title`, `detail` and,
  for validation, `problems: [{"field": "...", "message": "..."}]`. Instants are ISO-8601 in UTC
  (`2026-10-15T09:00:00Z`), dates are `yyyy-MM-dd`, enums are their Java names.
- Secrets (tokens, passwords, client secrets) come from environment variables that OpenShift fills from secrets;
  none is ever written to a repository, a log or a test fixture.
- Every repository builds and tests with `./gradlew build` and ships a `Dockerfile` plus
  `deploy/openshift/<app>/{base,overlays/{rd,qc,prod}}` (kustomize), on the pattern of DSOMgmtPortal.
- Commit messages end with
  `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01DK64WyTTEZusvWgXF1UdQX` on the last two lines.

## Calls between the services

Beadle-GUI calls Beadle-Internal and Beadle-ProTech; both call Beadle-GUI back. Every call carries:

| Header | Value |
|---|---|
| `Authorization` | `Bearer <service token>`: one shared secret per environment, `BEADLE_SERVICE_TOKEN` in all three services (`beadle.services.token`). A request without the right token is answered `401`. |
| `X-Beadle-User` | the user signed in to Beadle-GUI who caused the call (`system` for a scheduled or callback-triggered call). Internal and ProTech only log it; they sign in to the external systems with the generic service accounts of Beadle. |
| `X-Correlation-Id` | a UUID minted by Beadle-GUI per user action; Internal and ProTech log it and pass it on in callbacks. |

Timeouts: connect 5 s, read 30 s. Unreachable services or `5xx` answers surface in Beadle-GUI as a `503` with
`detail` naming the service ("Beadle-ProTech did not answer"), and the user action is still recorded in the audit
trail with outcome `FAILURE`.

Base URLs in Beadle-GUI: `beadle.services.internal.url` (`BEADLE_INTERNAL_URL`, local default
`http://localhost:8082`) and `beadle.services.protech.url` (`BEADLE_PROTECH_URL`, local default
`http://localhost:8083`). Base URL of Beadle-GUI in the two services: `beadle.gui.url` (`BEADLE_GUI_URL`, local
default `http://localhost:8081`). Ports: GUI 8081, Internal 8082, ProTech 8083.

## Beadle-ProTech REST API (`/api/protech`)

Mirrors the former `ServiceNowPort` and `ProTechLookupPort` of DSOMgmtPortal one to one, so that
`ProductionChangeService` in Beadle-GUI keeps working through a REST adapter.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/protech/status` | | `{"connected": false, "mode": "demo"}` or `{"connected": true, "mode": "live"}` |
| `POST /api/protech/changes` | a `Change` (the draft) | `201` `{"number": "CHG0001234", "url": "https://.../CHG0001234" or null}` |
| `POST /api/protech/changes/{number}/tasks` | a `ChangeTask` | `201` `{"number": "CTASK0001234"}` |
| `POST /api/protech/changes/read` | `[Change, ...]` (the changes Beadle-GUI knows) | `{"CHG0001234": Change, ...}`: the current state of each one as ProTech holds it |
| `PUT /api/protech/changes/{number}` | a `Change` | `204` |
| `POST /api/protech/changes/{number}/reminders` | `{"role": "BUSINESS" or null, "task": "CTASK0001234" or null}` | `["Rebecca Lawson", ...]`: the people reminded |
| `GET /api/protech/lookups/{kind}?q=&limit=20` | | `[{"value": "...", "detail": "..."}]`; `kind` is one of `users`, `departments`, `assignment-groups`, `releases`, `configuration-items`, `incidents`, `problems`, `clients` |

`Change` and `ChangeTask` are the JSON Jackson makes of the records `ProductionChange` and `ChangeTask` of
DSOMgmtPortal @ `278766b` (`domain/change`), with every nested record (`ChangeSchedule`, `ChangeTemplate` and its
`Approvers`, `Timing`, `Planning`, `PrivilegedAccess`, `TaskDetails`, `ChangeApproval`, `WorkflowStep`, `Reminder`,
`ChangeUpdate`) and enum (`ChangeState`, `ApprovalRole`, `ApprovalState`, `TaskState`) by its Java field names.
Beadle-GUI serialises its domain records; Beadle-ProTech has its own copy of the records (cut out with their
history) and must not rename a field without changing this document and Beadle-GUI together.

Callback to Beadle-GUI, sent whenever ProTech moved a change (state, approval, task) and Beadle-ProTech noticed
(the demo adapter notices when its clock-driven workflow advances; the live adapter when it polls ProTech or when
ProTech calls its webhook):

`POST {beadle.gui.url}/api/callbacks/protech` with
`{"number": "CHG0001234", "event": "STATE_CHANGED" | "APPROVAL_CHANGED" | "TASK_CHANGED", "state": "IMPLEMENTATION", "at": "2026-10-15T09:00:00Z", "correlationId": "..."}`.
Beadle-GUI answers `202`, reads the change again through `POST /api/protech/changes/read` and records the event in
the audit trail as actor `system:protech`.

## Beadle-Internal REST API (`/api/internal`)

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/internal/status` | | `{"jira": {"connected": false, "mode": "demo"}, "cyberTrack": {...}, "confluence": {...}, "qtest": {...}}` |
| `GET /api/internal/jira/projects/{key}/versions` | | `[{"name": "1.4.0", "released": false, "releaseDate": "2026-11-01" or null}]` (`JiraVersion`) |
| `GET /api/internal/jira/projects/{key}/epics?fixVersion=` | | `[{"key": "CERT-12", "summary": "...", "status": "In Progress", "epicKey": null, "updated": "2026-10-01"}]` (`JiraIssue`) |
| `GET /api/internal/jira/projects/{key}/stories?fixVersion=&epicKeys=CERT-12,CERT-13` | | `[JiraIssue]` with `epicKey` set |
| `POST /api/internal/cybertrack/tickets` | `{"changeNumber": "CHG0001234", "applicationName": "CertScanner", "implementationDate": "2026-10-20", "inputs": {"apoNumber": "...", "bitbucketUrl": "...", "artifactLink": "...", "qcApplicationLink": "..."}}` (the former `SecureCodingTicket`) | `201` `{"key": "SCP-123", "url": "https://.../browse/SCP-123" or null}` |
| `GET /api/internal/cybertrack/tickets/{key}` | | `{"key": "SCP-123", "status": "To Do", "url": ..., "updated": "2026-10-15T09:00:00Z"}` |
| `GET /api/internal/confluence/pages?spaceKey=&title=` | | `[{"id": "123", "spaceKey": "DSO", "title": "...", "version": 3, "url": "..."}]` |
| `GET /api/internal/confluence/pages/{id}` | | the page with `body` (Confluence storage format) |
| `POST /api/internal/confluence/pages` | `{"spaceKey": "DSO", "title": "...", "parentId": "123" or null, "body": "<p>...</p>"}` | `201` the page |
| `PUT /api/internal/confluence/pages/{id}` | `{"title": "...", "body": "...", "version": 3}` (the version being replaced) | the page with `version` + 1 |
| `GET /api/internal/qtest/projects` | | `[{"id": 1, "name": "..."}]` |
| `GET /api/internal/qtest/projects/{id}/test-cycles` | | `[{"id": 10, "name": "Release 1.4", "pid": "CL-10"}]` |
| `GET /api/internal/qtest/projects/{id}/test-runs?cycleId=` | | `[{"id": 100, "name": "...", "pid": "TR-100", "status": "Passed", "executedAt": "..." or null}]` |
| `GET /api/internal/qtest/projects/{id}/test-cycles/{cycleId}/summary` | | `{"total": 42, "passed": 40, "failed": 1, "blocked": 0, "unexecuted": 1}` |

Real clients: Jira Cloud/Server REST API v2 (`/rest/api/2/...`, bearer token of the Beadle service account),
CyberTrack the same API on its own base URL and token (`beadle.cybertrack.url`, `beadle.cybertrack.token`,
`project-key` default `SCP`, `issue-type` default `Task`; the former `JiraCyberTrackAdapter` is the starting point),
Confluence REST API (`/rest/api/content`, bearer token), qTest REST API v3 (`/api/v3/projects/...`, bearer token).
Each client lives behind a port with a demo adapter used when its URL is blank, exactly as `CyberTrackProperties`
did it with `CONFIGURED` / `NOT_CONFIGURED`.

Callback to Beadle-GUI, sent when a CyberTrack ticket Beadle created changes status (live adapter: polling every
`beadle.cybertrack.poll` = PT5M of the open tickets it created; demo adapter: a clock-driven transition):

`POST {beadle.gui.url}/api/callbacks/internal` with
`{"system": "CYBERTRACK", "key": "SCP-123", "status": "Done", "at": "...", "correlationId": "..."}`; Beadle-GUI
answers `202` and records it as actor `system:internal`.

## Audit trail in Beadle-GUI

Beadle-GUI is the system of record and the only place with an audit trail of user actions. Internal and ProTech
keep a call log of their own (who caused the call, correlation id, system, operation, target, outcome, message,
duration), in their own tables `BEADLE_INTERNAL_CALL_LOG` and `BEADLE_PROTECH_CALL_LOG`.

Table `BEADLE_ACTION_HISTORY` (Liquibase change set `028-action-history.sql` on top of the existing changelog, whose
files and checksums stay untouched): `ID`, `AT` (timestamp, UTC), `USER_NAME`, `ACTION`, `OBJECT_TYPE`,
`OBJECT_ID`, `OBJECT_KEY` (the CHG, CTASK, product code or ticket key a person would search for), `DETAILS`
(CLOB, JSON of the request without secrets, trimmed to 32 KB), `OUTCOME` (`SUCCESS` or `FAILURE`), `MESSAGE`
(the error message on failure), `CORRELATION_ID`, `DURATION_MS`. The application only inserts; nothing updates or
deletes a row. Indexes on `AT`, `USER_NAME`, `ACTION`, `OBJECT_KEY`.

Every use case that changes something or calls another system records exactly one row, in the same transaction
when there is one, and a `FAILURE` row when the use case throws. Actions (`ACTION` values):
`CHANGE_PREVIEWED`, `CHANGE_RAISED`, `CHANGE_EDITED`, `CHANGE_TASKS_CREATED`, `SECURE_CODING_TICKET_CREATED`,
`REMINDER_SENT`, `CHANGE_SYNCED`, `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DELETED`,
`CHANGE_PROFILE_UPDATED`, `DEPARTMENT_CREATED`, `DEPARTMENT_RENAMED`, `DEPARTMENT_DELETED`, `USER_SIGNED_IN`,
`CALLBACK_PROTECH`, `CALLBACK_INTERNAL`. Reads are not recorded, except `CHANGE_SYNCED` when opening a change
pulled a newer state from ProTech.

REST API: `GET /api/action-history?user=&action=&objectType=&objectKey=&outcome=&from=&to=&page=0&size=50` answers
`{"content": [...], "page": 0, "size": 50, "total": 1234}` newest first; `GET /api/action-history/users` and
`GET /api/action-history/actions` answer the distinct values for the filter lists. Page "Action History" at
`/action-history` in the Angular interface, listed in the sections of the application after Admin.

## Signing in to Beadle-GUI

Users sign in with Ping (OpenID Connect) through Spring Security's OAuth2 login; registration id `ping`,
`spring.security.oauth2.client.registration.ping.{client-id,client-secret}` from `PING_CLIENT_ID` /
`PING_CLIENT_SECRET`, `provider.ping.issuer-uri` from `PING_ISSUER_URI`, scopes `openid profile email`. The signed-in
user's name is `name` (fallback `preferred_username`, then `sub`) of the ID token. Profile `local` has no Ping:
`beadle.auth.mode=local` signs everybody in as `beadle.signed-in-user` (default `Mateusz Matan`); profiles rd, qc
and prod use `beadle.auth.mode=oidc`. `/api/**` requires a session except `/api/callbacks/**` (service token) and
`/actuator/health`; the single-page application routes are public and the Angular app sends the browser to
`/oauth2/authorization/ping` when an API call answers `401`. `GET /api/me` answers
`{"name": "...", "email": "..." or null}`; `POST /logout` ends the session. The user name goes into every audit row
and into `X-Beadle-User` of every call to Internal and ProTech; the external systems never see it.
