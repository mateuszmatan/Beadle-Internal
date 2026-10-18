# Beadle-Internal

Beadle-Internal connects Beadle to the systems of BBH's delivery teams. For Beadle-GUI it reads Jira versions, epics
and stories, raises and follows CyberTrack secure coding tickets, reads and writes Confluence pages, and reads qTest
projects, test cycles and test runs. It signs in to those systems with Beadle's generic service accounts, logs every
call it makes, and calls Beadle-GUI back when a CyberTrack ticket changes status.

Jira is the BBH Jira of the delivery teams. CyberTrack is the Jira of the Cybersecurity team, where secure coding
tickets are raised. Each has its own adapter and configuration.

## How it fits with the other two services

```
Beadle-GUI (8081) ──REST──> Beadle-Internal (8082) ──REST──> Jira, CyberTrack, Confluence, qTest
      ^                            │
      └──── callback: ticket status┘          Beadle-ProTech (8083) talks to ProTech (BBH's ServiceNow)
```

Only Beadle-GUI calls this service. Every call carries `Authorization: Bearer <BEADLE_SERVICE_TOKEN>`, the signed-in
user in `X-Beadle-User` and the user action in `X-Correlation-Id` (at most 64 characters). The endpoints, bodies,
headers and the callback are specified in [docs/CONTRACTS.md](docs/CONTRACTS.md), which is the same file in all three
repositories.

## Architecture

Hexagonal, with Spring MVC controllers as the way in. `ArchitectureSpec` enforces the layers.

```
com.bbh.itss.beadle.internal
├── domain/<area>              records and rules, no framework (jira, cybertrack, confluence, qtest, calllog, ...)
├── application/<area>         use cases behind port/in, ports to the outside in port/out
├── adapter/in/web             controllers, request records, error handling
├── adapter/in/schedule        the CyberTrack poll
├── adapter/out/<system>       jira, cybertrack, confluence, qtest: a live adapter, plus a demo one for the laptop
├── adapter/out/gui            the callbacks to Beadle-GUI
├── adapter/out/http           the shared HTTP client with logging and the call log
├── adapter/out/persistence    JPA: call log, followed CyberTrack tickets
└── config                     Spring wiring, the service token filter, properties
```

A CyberTrack ticket is answered as soon as it is created. A scheduled poll then reads all open tickets in one call
and posts each change of status to `{BEADLE_GUI_URL}/api/callbacks/internal`. A ticket in a final status is no longer
followed. A callback that Beadle-GUI does not accept is sent again on the next poll.

## Run on a BBH laptop

```bash
export BEADLE_SERVICE_TOKEN=local-$(whoami)     # the same value as for Beadle-GUI
./gradlew bootRun                               # http://localhost:8082, profile local
curl -H "Authorization: Bearer $BEADLE_SERVICE_TOKEN" http://localhost:8082/api/internal/status
```

The `local` profile (the default) uses an H2 file database in Oracle mode in `~/bbh-devsecops/beadle-internal`. It
uses the demo adapters for every system whose URL is not set. The demo CyberTrack moves its tickets from To Do through
In Progress to Done, two minutes per step. Set a system's URL and token to use the real one.

## Build and tests

The build uses the BBH Nexus only, so it runs inside the BBH network or on the VPN.

| Task | What | Typical time |
|---|---|---|
| `./gradlew build` | jar, all test suites, coverage gates | about 1.5 min |
| `./gradlew bootJar` | `build/libs/beadle-internal-<version>.jar` | 10 s |
| `./gradlew test` | unit specs (Spock), JaCoCo gate of 60% of lines | 20 s |
| `./gradlew smokeTest` | health, status, Jira versions, raising a ticket, 401 without the token | 15 s |
| `./gradlew regressionTest` | the key journeys over HTTP on H2 with the demo adapters | 20 s |
| `./gradlew performanceTest` | p95 latency of the Jira reads and ticket creation | 5 s |
| `./gradlew jacocoSuitesReport` | combined coverage of the three suites, gate of 30% | part of `build` |

## Configuration

Every setting comes from an environment variable. In OpenShift they come from the ConfigMap
`beadle-internal-config` and the secrets listed under Deployment.

| Property | Variable | Default | Meaning |
|---|---|---|---|
| `beadle.services.token` | `BEADLE_SERVICE_TOKEN` | blank: every call is refused | token shared by the three services |
| `beadle.gui.url` | `BEADLE_GUI_URL` | `http://localhost:8081` | where the callbacks go |
| `beadle.jira.url`, `.token` | `JIRA_URL`, `JIRA_TOKEN` | blank: demo on a laptop | BBH Jira and its service account token |
| `beadle.jira.epic-link-field` | `JIRA_EPIC_LINK_FIELD` | `customfield_10014` | the field linking a story to its epic |
| `beadle.cybertrack.url`, `.token` | `CYBERTRACK_URL`, `CYBERTRACK_TOKEN` | blank: demo on a laptop | CyberTrack and its token |
| `beadle.cybertrack.project-key` | `CYBERTRACK_PROJECT_KEY` | `SCP` | project of the secure coding tickets |
| `beadle.cybertrack.issue-type` | `CYBERTRACK_ISSUE_TYPE` | `Task` | issue type of the tickets |
| `beadle.cybertrack.poll` | `CYBERTRACK_POLL` | `PT5M` (`PT10S` locally) | how often open tickets are read |
| `beadle.cybertrack.closed-statuses` | `CYBERTRACK_CLOSED_STATUSES` | `Done,Closed,Resolved,Cancelled,Canceled,Rejected,Won't Do` | final statuses |
| `beadle.cybertrack.max-misses` | `CYBERTRACK_MAX_MISSES` | `12` | polls in a row a ticket may be missing before it is dropped |
| `beadle.confluence.url`, `.token` | `CONFLUENCE_URL`, `CONFLUENCE_TOKEN` | blank: demo on a laptop | Confluence |
| `beadle.qtest.url`, `.token` | `QTEST_URL`, `QTEST_TOKEN` | blank: demo on a laptop | qTest |
| `beadle.qtest.concurrency` | `QTEST_CONCURRENCY` | `8` | qTest reads running at once |
| `beadle.qtest.budget` | `QTEST_BUDGET` | `20s` | longest a qTest list or summary may take |

Also: `PORT` (`8082`), `SPRING_PROFILES_ACTIVE` (`local`), `BEADLE_INTERNAL_DATA_DIR` (H2 folder), `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE`, `LOG_PATH` (`./logs`). Tokens and passwords come only from the
environment.

| Profile | Database | Adapters |
|---|---|---|
| `local` (default) | H2 file in Oracle mode | demo where a URL is blank |
| `rd`, `qc`, `prod` | Oracle (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | live only; the service does not start while a system's URL is blank |

Liquibase updates the schema at start-up and Hibernate validates it. Logs go to the console and to a rolling file in
`LOG_PATH`.

## Deployment

```bash
./gradlew bootJar && docker build -t beadle-internal:0.1.0-SNAPSHOT .
```

The image runs as user 185 on port 8082, keeps the local H2 database in `/application/data` and writes logs to
`/application/logs`.

All three Beadle services run in one OpenShift project, one pod each. `deploy/openshift/beadle-internal` holds a
Kustomize base (Deployment, Service, ConfigMap) and the overlays `rd`, `qc` and `prod`, which set the profile and the
system URLs. Fill in `JIRA_URL`, `CYBERTRACK_URL`, `CONFLUENCE_URL` and `QTEST_URL` in the overlay before applying it.
There is no Route: Beadle-GUI reaches the service inside the project at `http://beadle-internal:8082`.

The Deployment reads the secrets `beadle-internal-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `beadle-services`
(`BEADLE_SERVICE_TOKEN`, shared by the three services) and, optionally, `beadle-internal-jira`,
`beadle-internal-cybertrack`, `beadle-internal-confluence` and `beadle-internal-qtest` (`<SYSTEM>_TOKEN`).
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) walks through a full deployment of the three services.

## Known gaps

- The connections to the real Jira, CyberTrack, Confluence and qTest have not been tried yet; their URLs and service
  account tokens must be filled in for each environment.
- The base image tag in the Dockerfile must be set to the BBH-approved UBI 9 OpenJDK 21 image, the same in all three
  repositories.
