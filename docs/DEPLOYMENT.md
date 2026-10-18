# Beadle Ecosystem - Quick Deployment & Run Guide

A concise guide to running the complete **Beadle** platform locally and deploying it to **OpenShift**.

Beadle consists of three cooperating services communicating via REST:
- **`Beadle-GUI`** (Port **8081**): Angular UI and core orchestrator / database of record.
- **`Beadle-Internal`** (Port **8082**): Integration with Jira, CyberTrack, Confluence, and qTest.
- **`Beadle-ProTech`** (Port **8083**): Integration with ProTech (ServiceNow) and approval workflows.

All three services require a shared security token configured in `BEADLE_SERVICE_TOKEN`.

---

## 1. Running Locally (All 3 Services)

On a BBH developer laptop, all three services can be run directly as native Java / Gradle applications without needing Docker, using embedded H2 databases.

### Option A: Side-by-Side via Gradle (Recommended - No Docker Needed)

1. **Set the shared token** in each terminal session:
   ```bash
   export BEADLE_SERVICE_TOKEN=local-dev
   ```

2. **Terminal 1 — Beadle-Internal:**
   ```bash
   cd /path/to/Beadle-Internal
   ./gradlew bootRun
   # Listens on http://localhost:8082 (H2 mode)
   ```

3. **Terminal 2 — Beadle-ProTech:**
   ```bash
   cd /path/to/Beadle-ProTech
   ./gradlew bootRun
   # Listens on http://localhost:8083 (H2 mode)
   ```

4. **Terminal 3 — Beadle-GUI (Backend + Bundled Frontend):**
   ```bash
   cd /path/to/Beadle-GUI
   ./gradlew :beadle-backend:bootRun
   # Listens on http://localhost:8081
   ```

5. **Open Browser:** `http://localhost:8081`.

---

### Option B: Docker Compose (All-in-One Alternative)
Runs all three services in containers with embedded H2 databases and demo adapters.

```bash
# 1. Navigate to docker deploy directory in Beadle-GUI
cd deploy/docker

# 2. Start all 3 services
export BEADLE_SERVICE_TOKEN=local-dev
docker compose up --build
```
- **Web UI**: `http://localhost:8081`
- **Default User**: `Mateusz Matan`

To stop: `docker compose down`.

---

## 2. Deploying to OpenShift (Single Instance per Service on Oracle DB)

All services deploy as a single pod (`replicas: 1`) connected to Oracle DB on test (`rd`, `qc`) and production (`prod`) environments. Redundant instances are not needed because OpenShift automatically respawns any pod if it becomes unavailable.

### Step 1: Build and Push Container Images
In each repository:
```bash
# Beadle-Internal
cd /path/to/Beadle-Internal && ./gradlew bootJar
docker build -t <registry>/<project>/beadle-internal:latest .
docker push <registry>/<project>/beadle-internal:latest

# Beadle-ProTech
cd /path/to/Beadle-ProTech && ./gradlew bootJar
docker build -t <registry>/<project>/beadle-protech:latest .
docker push <registry>/<project>/beadle-protech:latest

# Beadle-GUI
cd /path/to/Beadle-GUI && ./gradlew beadleJar
docker build -t <registry>/<project>/beadle:latest .
docker push <registry>/<project>/beadle:latest
```

### Step 2: Create OpenShift Secrets

1. **Shared Service Token**:
   ```bash
   oc create secret generic beadle-services \
     --from-literal=BEADLE_SERVICE_TOKEN='<production-shared-secret>'
   ```

2. **Oracle Database Secrets**:
   ```bash
   oc create secret generic beadle-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'

   oc create secret generic beadle-internal-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'

   oc create secret generic beadle-protech-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'
   ```

3. **External Service Accounts** (required on rd, qc and prod, which use only the real systems; the demo adapters run on a laptop only):
   ```bash
   oc create secret generic beadle-internal-jira --from-literal=JIRA_TOKEN='<token>'
   oc create secret generic beadle-internal-cybertrack --from-literal=CYBERTRACK_TOKEN='<token>'
   oc create secret generic beadle-internal-confluence --from-literal=CONFLUENCE_TOKEN='<token>'
   oc create secret generic beadle-internal-qtest --from-literal=QTEST_TOKEN='<token>'
   oc create secret generic beadle-protech-account \
     --from-literal=PROTECH_URL='https://<protech-host>' \
     --from-literal=PROTECH_USERNAME='<user>' --from-literal=PROTECH_PASSWORD='<password>' \
     --from-literal=PROTECH_WEBHOOK_TOKEN='<webhook-token>'
   ```

### Step 3: Apply Kustomize Overlays (`rd`, `qc`, or `prod`)

```bash
# 1. Deploy Beadle-Internal
cd /path/to/Beadle-Internal/deploy/openshift/beadle-internal/overlays/rd
kustomize edit set image beadle-internal=<registry>/<project>/beadle-internal:latest
oc apply -k .

# 2. Deploy Beadle-ProTech
cd /path/to/Beadle-ProTech/deploy/openshift/beadle-protech/overlays/rd
kustomize edit set image beadle-protech=<registry>/<project>/beadle-protech:latest
oc apply -k .

# 3. Deploy Beadle-GUI
cd /path/to/Beadle-GUI/deploy/openshift/beadle/overlays/rd
kustomize edit set image beadle=<registry>/<project>/beadle:latest
oc apply -k .
```

### Step 4: Verify Deployment
```bash
# Check all pods (1 replica each)
oc get pods -l 'app in (beadle, beadle-internal, beadle-protech)'

# Get public Beadle URL
oc get route beadle
```
