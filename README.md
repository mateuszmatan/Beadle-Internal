# Beadle-Internal

Beadle-Internal is the service between Beadle and the systems of BBH's delivery teams. It answers Beadle-GUI with
the Jira versions, epics and stories of a project, raises and follows CyberTrack secure coding tickets, finds,
reads, creates and updates Confluence pages and reads qTest projects, test cycles, test runs and their summaries.
It signs in to those systems with the generic service accounts of Beadle, keeps a call log of every call it makes
and calls Beadle-GUI back when a CyberTrack ticket it created changes status.

**Jira** is the BBH Jira of the delivery teams (versions, epics, stories); **CyberTrack** is the Jira of the
Cybersecurity team where the secure coding tickets are raised. The two have their own adapters, clients and
configuration.

[docs/CONTRACTS.md](docs/CONTRACTS.md) is the contract between the three Beadle repositories (Beadle-GUI,
Beadle-Internal and Beadle-ProTech): the REST API of this service with its bodies, the headers every call carries
and the callback to Beadle-GUI. The code follows it to the letter.

## API

Every call carries `Authorization: Bearer <BEADLE_SERVICE_TOKEN>` (otherwise `401`), and from Beadle-GUI
`X-Beadle-User` (the signed-in user, only logged; percent-encoded in UTF-8 as `URLEncoder` writes it, so that a
name such as `Łukasz Wójcik` survives the header as `%C5%81ukasz+W%C3%B3jcik`, and decoded before it is logged, a
value that is not valid percent-encoding taken as it came; the decoded name is cut to the 200 bytes the call log
holds, `unknown` when missing) and `X-Correlation-Id` (one per user action, logged and passed on in callbacks; a
fresh one is minted when it is missing, and one longer than 100 bytes is refused with `400`, since cutting it would
lose the callbacks of the action). Answers are JSON, errors `application/problem+json` with
`status`, `title`, `detail` and, for validation, `problems: [{"field", "message"}]`. A system that cannot be
reached or refuses the call answers `502` naming the system and its reason.

| Method and path | Answer |
|---|---|
| `GET /api/internal/status` | `connected` and `mode` (`demo` or `live`) of `jira`, `cyberTrack`, `confluence`, `qtest` |
| `GET /api/internal/jira/projects/{key}/versions` | the versions of a project, unreleased first |
| `GET /api/internal/jira/projects/{key}/epics?fixVersion=` | the epics of a FixVersion: those that carry it and those with an issue in it, newest change first |
| `GET /api/internal/jira/projects/{key}/stories?fixVersion=&epicKeys=` | the stories of chosen epics, with their `epicKey` |
| `POST /api/internal/cybertrack/tickets` | `201` the key (`SCP-123`) and link of the new secure coding ticket, answered straight after its creation; a change whose ticket is still followed gets that ticket again instead of a second one |
| `GET /api/internal/cybertrack/tickets/{key}` | the status of a ticket and when it last changed |
| `GET /api/internal/confluence/pages?spaceKey=&title=` | the pages of a space with that title, without their body |
| `GET /api/internal/confluence/pages/{id}` | a page with its body in storage format |
| `POST /api/internal/confluence/pages` | `201` the new page |
| `PUT /api/internal/confluence/pages/{id}` | the page one version up; `409` when `version` is not the current one |
| `GET /api/internal/qtest/projects` | the qTest projects |
| `GET /api/internal/qtest/projects/{id}/test-cycles` | the test cycles of a project, flat: those under the root then those of each release, each followed by the cycles nested in it |
| `GET /api/internal/qtest/projects/{id}/test-runs?cycleId=` | the test runs of a cycle and of the cycles and suites in it, with their latest result and when it ended (or started); the last result is read only for runs that were executed or have no status |
| `GET /api/internal/qtest/projects/{id}/test-cycles/{cycleId}/summary` | the runs of a cycle counted by result, from the Status of each run without a call per run |
| `GET /api/internal/call-log?correlationId=&from=&to=&page=0&size=50` | the call log, newest first, for diagnosis |
| `GET /actuator/health` | open, without a token |

### Callbacks to Beadle-GUI

A CyberTrack ticket created through the API is written down in `BEADLE_INTERNAL_CYBERTRACK_TICKET` with its change,
its link and the correlation id of the user action, and answered at once: the creation is the only call to CyberTrack
within the request, and the first poll tells Beadle-GUI the status the ticket starts in. A second request for a
change whose ticket is still followed (Beadle-GUI retrying after its own time-out) is answered with that ticket. A
scheduled poll reads the open tickets in one call, every 10 seconds while the demo adapter moves tickets on its clock
(To Do, In Progress, Done, two minutes per step from the creation time the table keeps, so a restart does not move
them on, and new demo keys continue after the highest one in the table) and every `beadle.cybertrack.poll` once
CyberTrack is configured, and for every ticket that moved posts
`{"system": "CYBERTRACK", "key", "status", "at", "correlationId"}` to `{beadle.gui.url}/api/callbacks/internal` with
the service token and `X-Beadle-User: system`. A ticket that reached a
final status (`beadle.cybertrack.closed-statuses`, by default Done, Closed, Resolved, Cancelled, Canceled, Rejected,
Won't Do) is no longer followed, nor one that the poll did not find `beadle.cybertrack.max-misses` times in a row
(deleted, or out of reach of the service account), which is logged as dropped. A callback Beadle-GUI does not take is
logged and sent again on the next tick; the poll never fails a request. Each ticket remembers whether the demo made
it up: the real CyberTrack is never asked about a demo key, which may belong to another ticket there, nor the demo
about a real one, so the open tickets of the other kind are dropped with a warning once `CYBERTRACK_URL` is set or
cleared.

### Call log

`BEADLE_INTERNAL_CALL_LOG` holds one row per call to Jira, CyberTrack, Confluence, qTest or Beadle-GUI, demo adapters
included: when, the user (`X-Beadle-User` decoded, `unknown` without it, or `system` for the poll), the correlation
id, the system, the operation, its target, `SUCCESS` or `FAILURE` with the message, and the duration. The row is
written in its own transaction, and a call log that cannot be written never fails the call.

## Repository layout

```
settings.gradle, build.gradle   one Spring Boot module: whether the BBH Nexus is reachable, the dependencies, the test suites
src/main/java                   the service, package com.bbh.itss.beadle.internal (hexagonal: adapter, application, domain, config)
src/main/resources              application.yml with the profiles, the Liquibase changelog (call log, followed CyberTrack tickets)
src/test, src/testFixtures      the Spock specs and the fixtures they share
src/regressionTest              the API end to end on H2 with the demo adapters
deploy/openshift/beadle-internal   the Kustomize base and the rd, qc and prod overlays
Dockerfile                      the container image, on the Red Hat UBI 9 OpenJDK 21 runtime
docs/CONTRACTS.md               the contract between Beadle-GUI, Beadle-Internal and Beadle-ProTech
```

The layers: `adapter/in/web` (the controllers and the error handling), `adapter/in/schedule` (the CyberTrack poll),
`adapter/out/<system>` (a demo and a live adapter per system, `gui` for the callbacks, `persistence` for the
database), `application/<area>` (the use cases behind `port/in`, the ports to the outside in `port/out`),
`domain/<area>` (records and rules, no framework) and `config` (the service token filter, transactions, call log,
scheduling). `ArchitectureSpec` keeps them apart.

## Build

Needs Java 21 on the `PATH`; the Gradle wrapper downloads Gradle 8.14 from the BBH Nexus inside BBH and from
services.gradle.org outside (`settings.gradle` makes one `HEAD` request to the BBH Nexus to find out where it is;
`-PbbhNetwork=false` or `true` overrides the check).

```bash
./gradlew build                 # the jar, every suite and the coverage gate (60% of lines)
./gradlew bootJar               # build/libs/beadle-internal-<version>.jar
./gradlew test                  # the unit specs
./gradlew regressionTest        # the API end to end on H2 with the demo adapters
./gradlew publish -Pnexus.snapshotsUrl=<url>   # the jar to the BBH Nexus (bbhNexusUsername and bbhNexusPassword as Gradle properties)
./gradlew bootJar -PartifactVersion=1.0.0
```

## Configuration

Every setting comes from an environment variable; OpenShift fills them from the ConfigMap `beadle-internal-config`
and the secrets below. A system whose URL is blank is played by its demo adapter; the `rd`, `qc` and `prod` profiles
refuse to start while a system's URL is blank, unless its `<SYSTEM>_DEMO=true` asks for the demo on purpose.

| Property | Variable | Default | What |
|---|---|---|---|
| `beadle.services.token` | `BEADLE_SERVICE_TOKEN` | blank: every call is refused | the token shared by the three Beadle services, checked on every call and sent with the callbacks; whitespace around it, such as the newline of a secret written from a file, is dropped both ways, as from every `<SYSTEM>_TOKEN` |
| `beadle.gui.url` | `BEADLE_GUI_URL` | `http://localhost:8081` | where Beadle-GUI takes the callbacks |
| `beadle.jira.url` | `JIRA_URL` | blank: demo | the BBH Jira of the delivery teams |
| `beadle.jira.token` | `JIRA_TOKEN` | blank | bearer token of the Beadle service account in Jira |
| `beadle.jira.demo` | `JIRA_DEMO` | `false` | `true` lets `rd`, `qc` and `prod` start with a blank `JIRA_URL`, on the demo |
| `beadle.jira.epic-link-field` | `JIRA_EPIC_LINK_FIELD` | `customfield_10014` | the field linking a story to its epic (the parent is read too) |
| `beadle.cybertrack.url` | `CYBERTRACK_URL` | blank: demo | CyberTrack, the Jira of the Cybersecurity team |
| `beadle.cybertrack.token` | `CYBERTRACK_TOKEN` | blank | bearer token of the Beadle service account in CyberTrack |
| `beadle.cybertrack.demo` | `CYBERTRACK_DEMO` | `false` | `true` lets `rd`, `qc` and `prod` start with a blank `CYBERTRACK_URL`, on the demo |
| `beadle.cybertrack.project-key` | `CYBERTRACK_PROJECT_KEY` | `SCP` | the project of the secure coding tickets |
| `beadle.cybertrack.issue-type` | `CYBERTRACK_ISSUE_TYPE` | `Task` | the issue type of the secure coding tickets |
| `beadle.cybertrack.poll` | `CYBERTRACK_POLL` | `PT5M` | how often the open tickets are polled once CyberTrack is configured (the demo is checked every 10 seconds) |
| `beadle.cybertrack.closed-statuses` | `CYBERTRACK_CLOSED_STATUSES` | `Done,Closed,Resolved,Cancelled,Canceled,Rejected,Won't Do` | the statuses (any case) that end the follow-up of a ticket; list the final statuses of the CyberTrack workflow |
| `beadle.cybertrack.max-misses` | `CYBERTRACK_MAX_MISSES` | `12` | how many polls in a row may miss a ticket before it is no longer followed |
| `beadle.confluence.url` | `CONFLUENCE_URL` | blank: demo | Confluence |
| `beadle.confluence.token` | `CONFLUENCE_TOKEN` | blank | bearer token of the Beadle service account in Confluence |
| `beadle.confluence.demo` | `CONFLUENCE_DEMO` | `false` | `true` lets `rd`, `qc` and `prod` start with a blank `CONFLUENCE_URL`, on the demo |
| `beadle.qtest.url` | `QTEST_URL` | blank: demo | qTest |
| `beadle.qtest.token` | `QTEST_TOKEN` | blank | bearer token of the Beadle service account in qTest |
| `beadle.qtest.demo` | `QTEST_DEMO` | `false` | `true` lets `rd`, `qc` and `prod` start with a blank `QTEST_URL`, on the demo |
| `beadle.qtest.concurrency` | `QTEST_CONCURRENCY` | `8` | the most qTest reads under way at once across all requests (last results of runs, cycles of releases), 1 to 50 |
| `beadle.qtest.budget` | `QTEST_BUDGET` | `20s` | the longest a list of cycles or runs, or a summary, may take before it is refused with a `502`, below the 30 s Beadle-GUI waits |

The other variables: `PORT` (`8082`), `SPRING_PROFILES_ACTIVE` (`local`), `BEADLE_INTERNAL_DATA_DIR` (the H2 folder
of the `local` profile, `~/bbh-devsecops/beadle-internal`), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`DB_POOL_SIZE` (the database). Every client connects within 5 seconds and reads within 30
(`spring.http.clients.*`). Tokens and passwords come only from the environment; none is written to the repository
or the log.

## Running locally

```bash
export BEADLE_SERVICE_TOKEN=<the token Beadle-GUI sends>
./gradlew bootRun               # http://localhost:8082, profile local
curl -H "Authorization: Bearer $BEADLE_SERVICE_TOKEN" http://localhost:8082/api/internal/status
```

The `local` profile (the default) keeps an H2 database in `~/bbh-devsecops/beadle-internal`
(`BEADLE_INTERNAL_DATA_DIR` moves it) and uses the demo adapters of Jira, CyberTrack, Confluence and qTest; a real
system is connected by setting its URL and token. Beadle-GUI is expected on `http://localhost:8081` for the
callbacks; while it is not running, the callbacks are logged as failed and sent again on the next tick. In VS Code
the tasks `gradle: bootRun`, `gradle: test`, `gradle: regressionTest` and `gradle: build` do the same.

## Profiles

| Profile | Database | Adapters |
|---------|----------|----------|
| `local` (default) | H2 file in Oracle mode | demo, unless a system's URL is set |
| `rd`, `qc`, `prod` | Oracle from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (`DB_POOL_SIZE` sizes the pool) | the real ones: the service does not start while a system's URL is blank, unless `<SYSTEM>_DEMO=true` asks for its demo |

`rd`, `qc` and `prod` each switch on the `oracle` profile. Liquibase updates the schema on every start and
Hibernate validates it on Oracle. `rd` logs at `DEBUG`; `rd` and `qc` show the health details.

## Containers and OpenShift

```bash
./gradlew bootJar
docker build -t beadle-internal:0.1.0-SNAPSHOT .
docker run --rm -p 8082:8082 -e BEADLE_SERVICE_TOKEN=<token> -e BEADLE_GUI_URL=http://host.docker.internal:8081 \
  beadle-internal:0.1.0-SNAPSHOT
```

The image runs as user 185, listens on 8082 and keeps the H2 database of the `local` profile in the volume
`/application/data`.

The manifests in `deploy/openshift/beadle-internal` are a Kustomize base (Deployment, Service, Route and the
ConfigMap `beadle-internal-config` with the profile, `BEADLE_GUI_URL=http://beadle:8081`, the system URLs and the
CyberTrack settings) with one overlay per environment that sets its profile and the URLs of its systems. The
overlays ship those URLs blank: fill in `JIRA_URL`, `CYBERTRACK_URL`, `CONFLUENCE_URL` and `QTEST_URL` in the
overlay's `configMapGenerator` before applying it, since `rd`, `qc` and `prod` do not start while one is blank
(a demo adapter would otherwise answer in its place, and a demo CyberTrack would hand Beadle-GUI made-up tickets).
Add `<SYSTEM>_DEMO=true` next to them only to run a system on its demo on purpose. The Deployment reads the secrets
`beadle-internal-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and `beadle-internal-services`
(`BEADLE_SERVICE_TOKEN`), and the optional `beadle-internal-jira`, `beadle-internal-cybertrack`,
`beadle-internal-confluence` and `beadle-internal-qtest` (`<SYSTEM>_TOKEN`); its probes are
`/actuator/health/liveness` and `/actuator/health/readiness`.

```bash
oc create secret generic beadle-internal-db --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
  --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'
oc create secret generic beadle-internal-services --from-literal=BEADLE_SERVICE_TOKEN='<token>'
oc create secret generic beadle-internal-jira --from-literal=JIRA_TOKEN='<token>'
cd deploy/openshift/beadle-internal/overlays/rd
kustomize edit set image beadle-internal=<registry>/<project>/beadle-internal:0.1.0-SNAPSHOT
# set JIRA_URL, CYBERTRACK_URL, CONFLUENCE_URL and QTEST_URL in kustomization.yaml, then
oc apply -k .
```

The Deployment runs one replica with the `Recreate` strategy: the CyberTrack poll runs in every pod and each would
send the same callbacks, so a rollout stops the old pod before the new one starts, and Beadle-Internal is away for
the seconds the new pod takes to start. Keep it at one replica.

## Tests

| Suite | What |
|-------|------|
| unit (`src/test`) | domain rules, use cases, the controllers through MockMvc (including `401` without or with a wrong token), the live adapters against `MockRestServiceServer`, the demo adapters, the Liquibase change sets on H2, the profiles and the architecture rules; JaCoCo fails `build` below 60% line coverage (`-Pcoverage.minimum=`) |
| regression (`src/regressionTest`) | the API end to end on H2 in Oracle mode with the demo adapters, one spec per controller over HTTP: the status reporting `demo`, Jira, CyberTrack with its callbacks taken by a stand-in Beadle-GUI as a movable clock moves the tickets, Confluence, qTest and the call log filling up |

`ArchitectureSpec` checks the hexagonal layers: the domain and the application use no framework, adapters in and
out stay apart, controllers live in `adapter/in/web`, the scheduled poll in `adapter/in`, every out port is
implemented in `adapter/out`, only the HTTP adapters use an HTTP client, and no package depends on itself in a
cycle.
