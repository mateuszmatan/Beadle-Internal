# Beadle: running and deploying the three services

Beadle is three services that talk REST to each other; this file is the same in all three repositories, and
[CONTRACTS.md](CONTRACTS.md) specifies what passes between them.

- **Beadle-GUI** (port **8081**): the Angular interface and the system of record, with the audit trail.
- **Beadle-Internal** (port **8082**): Jira, CyberTrack, Confluence and qTest.
- **Beadle-ProTech** (port **8083**): ProTech, BBH's ServiceNow.

All three take the same token from `BEADLE_SERVICE_TOKEN`. They build from the BBH Nexus and the BBH npm registry, so
inside the BBH network or on the VPN.

## 1. On a BBH laptop

Each service runs with the profile `local`: its own H2 file database in Oracle mode and demo adapters for the systems
behind it, so nothing reaches Jira, CyberTrack or ProTech. Beadle-GUI signs everybody in as `Mateusz Matan`.

### Option A: three terminals, no Docker

```bash
export BEADLE_SERVICE_TOKEN=local-dev            # in each terminal

cd /path/to/Beadle-Internal && ./gradlew bootRun               # http://localhost:8082
cd /path/to/Beadle-ProTech  && ./gradlew bootRun               # http://localhost:8083
cd /path/to/Beadle-GUI      && ./gradlew :beadle-backend:bootRun   # http://localhost:8081
```

Open http://localhost:8081. The services may start in any order; a callback that was not taken is sent again.

### Option B: Docker Compose

`deploy/docker/compose.yaml` in Beadle-GUI runs the three images on one network under their OpenShift names, with H2
in volumes. It expects the three clones side by side (`BEADLE_INTERNAL_DIR` and `BEADLE_PROTECH_DIR` name others)
and their jars built:

```bash
(cd /path/to/Beadle-Internal && ./gradlew bootJar)
(cd /path/to/Beadle-ProTech && ./gradlew bootJar)
cd /path/to/Beadle-GUI && ./gradlew beadleJar
cd deploy/docker && BEADLE_SERVICE_TOKEN=local-dev docker compose up --build
```

Only Beadle-GUI is published, on http://localhost:8081. `docker compose down` stops the three.

## 2. On OpenShift (rd, qc, prod)

The three services run in one OpenShift project, one pod each (`replicas: 1`), on Oracle, with the real systems only:
a service does not start while an address or account it needs is missing. OpenShift restarts a pod that fails.

### Step 1: build and push the images

```bash
cd /path/to/Beadle-Internal && ./gradlew bootJar
docker build -t <registry>/<project>/beadle-internal:<version> . && docker push <registry>/<project>/beadle-internal:<version>

cd /path/to/Beadle-ProTech && ./gradlew bootJar
docker build -t <registry>/<project>/beadle-protech:<version> . && docker push <registry>/<project>/beadle-protech:<version>

cd /path/to/Beadle-GUI && ./gradlew beadleJar
docker build -t <registry>/<project>/beadle:<version> . && docker push <registry>/<project>/beadle:<version>
```

Give every build a new `<version>`: the pods pull an image only when their node does not have that tag yet.

### Step 2: create the secrets

```bash
# the token the three services share
oc create secret generic beadle-services --from-literal=BEADLE_SERVICE_TOKEN='<token>'

# one Oracle schema per service
for s in beadle beadle-internal beadle-protech; do
  oc create secret generic $s-db --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
    --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'
done

# Beadle-GUI's OpenID Connect client at Ping
oc create secret generic beadle-oidc \
  --from-literal=PING_CLIENT_ID='<client id>' --from-literal=PING_CLIENT_SECRET='<client secret>'

# Beadle's service accounts of the external systems
oc create secret generic beadle-internal-jira --from-literal=JIRA_TOKEN='<token>'
oc create secret generic beadle-internal-cybertrack --from-literal=CYBERTRACK_TOKEN='<token>'
oc create secret generic beadle-internal-confluence --from-literal=CONFLUENCE_TOKEN='<token>'
oc create secret generic beadle-internal-qtest --from-literal=QTEST_TOKEN='<token>'
oc create secret generic beadle-protech-account \
  --from-literal=PROTECH_URL='https://<protech-host>' \
  --from-literal=PROTECH_USERNAME='<user>' --from-literal=PROTECH_PASSWORD='<password>' \
  --from-literal=PROTECH_WEBHOOK_TOKEN='<webhook-token>'
```

### Step 3: fill in the overlays and apply them

Each repository has `deploy/openshift/<app>/overlays/{rd,qc,prod}`. Before the first deployment of an environment:

- in Beadle-Internal's overlay, set `JIRA_URL`, `CYBERTRACK_URL`, `CONFLUENCE_URL` and `QTEST_URL`;
- in Beadle-GUI's overlay, replace the placeholder `PING_ISSUER_URI` with the issuer of Beadle's client at Ping, and
  register `https://<route>/login/oauth2/code/ping` at Ping as its redirect URI.

```bash
for app in beadle-internal beadle-protech beadle; do       # Beadle-GUI's folder is deploy/openshift/beadle
  cd /path/to/<repository of $app>/deploy/openshift/$app/overlays/rd
  kustomize edit set image $app=<registry>/<project>/$app:<version>
  oc apply -k .
done
```

Beadle-GUI reaches the two services inside the project at `http://beadle-internal:8082` and
`http://beadle-protech:8083`, and they call it back at `http://beadle:8081`. Only Beadle-GUI has a Route. Once ProTech
calls Beadle-ProTech's webhook, its overlay adds the component `webhook-route`, which publishes that path alone.

### Step 4: check

```bash
oc get pods -l 'app.kubernetes.io/name in (beadle, beadle-internal, beadle-protech)'
oc get route beadle
```
