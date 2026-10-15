# Beadle-Internal

Beadle-Internal is the service between Beadle and the systems of BBH's delivery teams. It answers Beadle-GUI with
the Jira versions, epics and stories of a project, raises and follows CyberTrack secure coding tickets, finds,
reads, creates and updates Confluence pages and reads qTest projects, test cycles, test runs and their summaries.
It signs in to those systems with the generic service accounts of Beadle, keeps a call log of every call it makes
and calls Beadle-GUI back when a CyberTrack ticket it created changes status.

[docs/CONTRACTS.md](docs/CONTRACTS.md) is the contract between the three Beadle repositories (Beadle-GUI,
Beadle-Internal and Beadle-ProTech): the REST API of this service, the headers every call carries and the callback
to Beadle-GUI. The code follows it to the letter.

## Repository layout

```
settings.gradle, build.gradle   one Spring Boot module: whether the BBH Nexus is reachable, the dependencies, the test suites
src/main/java                   the service, package com.bbh.itss.beadle.internal (hexagonal: adapter, application, domain, config)
src/main/resources              application.yml with the profiles and the Liquibase changelog of the call log
src/test, src/testFixtures      the Spock specs and the fixtures they share
src/regressionTest              the API end to end on H2 with the demo adapters
deploy/openshift/beadle-internal   the Kustomize base and the rd, qc and prod overlays
Dockerfile                      the container image, on the Red Hat UBI 9 OpenJDK 21 runtime
```

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

## Running locally

```bash
export BEADLE_SERVICE_TOKEN=<the token Beadle-GUI sends>
./gradlew bootRun               # http://localhost:8082, profile local
```

The `local` profile (the default) keeps an H2 database in `~/bbh-devsecops/beadle-internal`
(`BEADLE_INTERNAL_DATA_DIR` moves it) and uses the demo adapters of Jira, CyberTrack, Confluence and qTest; a real
system is connected by setting its URL and token. Every call to `/api/internal/...` must carry
`Authorization: Bearer <BEADLE_SERVICE_TOKEN>`; without the token the service answers `401`, and without the
variable it refuses every call. `GET /actuator/health` is open.

## Profiles

| Profile | Database | Adapters |
|---------|----------|----------|
| `local` (default) | H2 file in Oracle mode | demo, unless a system's URL is set |
| `rd`, `qc`, `prod` | Oracle from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (`DB_POOL_SIZE` sizes the pool) | the real ones, one per system whose URL is set |

Liquibase updates the schema on every start and Hibernate validates it.

## Containers and OpenShift

```bash
./gradlew bootJar
docker build -t beadle-internal:0.1.0-SNAPSHOT .
docker run --rm -p 8082:8082 -e BEADLE_SERVICE_TOKEN=<token> beadle-internal:0.1.0-SNAPSHOT
```

The manifests in `deploy/openshift/beadle-internal` are a Kustomize base (Deployment, Service, Route and the
ConfigMap `beadle-internal-config`) with one overlay per environment. The Deployment reads the secrets
`beadle-internal-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and `beadle-internal-services`
(`BEADLE_SERVICE_TOKEN`), and the optional `beadle-internal-jira`, `beadle-internal-cybertrack`,
`beadle-internal-confluence` and `beadle-internal-qtest` (`<SYSTEM>_TOKEN`):

```bash
oc create secret generic beadle-internal-db --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
  --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'
oc create secret generic beadle-internal-services --from-literal=BEADLE_SERVICE_TOKEN='<token>'
oc create secret generic beadle-internal-jira --from-literal=JIRA_TOKEN='<token>'
cd deploy/openshift/beadle-internal/overlays/rd
kustomize edit set image beadle-internal=<registry>/<project>/beadle-internal:0.1.0-SNAPSHOT
oc apply -k .
```

## Tests

| Suite | What |
|-------|------|
| unit (`src/test`) | domain rules, use cases, adapters and the architecture rules; JaCoCo fails `build` below 60% line coverage (`-Pcoverage.minimum=`) |
| regression (`src/regressionTest`) | the API end to end on H2 in Oracle mode with the demo adapters |
