# Docker Assignment 2 - Optimization, Networking, Volumes & Security

Builds on the [Docker Assignment 1](https://github.com/bbiradarvijaykumar-nagarro/docker-assignment-1)
Task Manager app to cover Dockerfile optimization, custom networking, volume
backup/restore, and security hardening, plus a Loki/Grafana log-aggregation
bonus.

## Stack

- Java 17, Spring Boot 3.3 (same Task Manager app as assignment 1)
- MySQL 8.4
- Nginx (reverse proxy, unprivileged image)
- Grafana Loki + Promtail + Grafana (bonus: log aggregation)
- Docker / Docker Compose
- Trivy + Docker Bench for Security (security auditing)
- GitHub Actions

## Project layout

```
app/
  Dockerfile.before        naive, unoptimized single-stage Dockerfile ("existing" baseline)
  Dockerfile                optimized multi-stage build with BuildKit cache mounts
  entrypoint.sh              reads DB password from a Docker secret if present
  src/...                    Spring Boot source (unchanged from assignment 1)
docker/
  nginx/nginx.conf            bind-mounted reverse proxy config
  promtail/promtail-config.yml bind-mounted log-shipping config
  grafana/provisioning/...    bind-mounted datasource + dashboard-as-code
scripts/
  backup-volume.sh           tars a named volume to the host
  restore-volume.sh          untars a backup back into a (new) named volume
secrets/
  *.txt.example              templates; copy to *.txt and edit before running
docker-compose.yml
.github/workflows/docker-ci.yml
```

## 1. Dockerfile optimization

`app/Dockerfile.before` is the naive starting point: a single stage built
`FROM maven:3.9-eclipse-temurin-17`, so the ~500MB+ JDK/Maven toolchain ships
inside the final image, there's no dependency-layer caching (every code
change re-downloads all of Maven Central), and the app runs as root.

`app/Dockerfile` is the optimized version:

- **Multi-stage build** - the JDK/Maven stage is discarded; the runtime image
  is `eclipse-temurin:17-jre-alpine`, roughly a third of the size.
- **Layer ordering for cache reuse** - `pom.xml` is copied and its
  dependencies resolved *before* the source is copied, so editing app code
  doesn't invalidate the dependency-download layer.
- **BuildKit cache mounts** (`--mount=type=cache,target=/root/.m2`) - keeps
  the local Maven repo warm across CI runs instead of re-fetching every time.
- **Non-root user** - runs as an unprivileged `spring` user, not root.
- **HEALTHCHECK** - so `docker ps` / Compose report real container health.

The `dockerfile-optimization` job in CI builds both, times each build, and
prints an image-size / build-time comparison table to the job summary - see
the Actions run linked in the repo for actual numbers.

## 2. Custom network

`docker-compose.yml` defines an explicitly-named bridge network,
`docker-assignment2-net`, that every service attaches to instead of relying
on Compose's default auto-generated network. The `network-and-volume-demo`
CI job also creates and manages a network by hand:

```bash
docker network create demo-net
docker network ls
docker network inspect demo-net
docker network connect bridge demo-mysql
docker network disconnect bridge demo-mysql
docker network rm demo-net
```

## 3. Volumes: named volumes + bind mounts together

Named volumes (persist data across container recreation):
- `mysql-data` - MySQL's data directory
- `grafana-data` - Grafana's dashboards/settings database
- `loki-data` - Loki's log chunk storage

Bind mounts (host file becomes visible/editable without an image rebuild):
- `./docker/nginx/nginx.conf` -> nginx's config
- `./docker/promtail/promtail-config.yml` -> Promtail's config
- `./docker/grafana/provisioning` -> Grafana datasource/dashboard-as-code
- `/var/run/docker.sock` and `/var/lib/docker/containers` -> Promtail (so it
  can discover and tail every container's logs)

The CI network/volume job demonstrates both on a *single* container at once:
MySQL gets its data directory as a named volume (`demo-data`) and a tuning
file as a bind mount (`ci-tmp/custom.cnf`) simultaneously - the same pattern
used for real services in `docker-compose.yml`.

## 4. Volume backup & restore

```bash
./scripts/backup-volume.sh mysql-data ./backups
./scripts/restore-volume.sh mysql-data ./backups/mysql-data-<timestamp>.tar.gz
```

Both scripts use a disposable `alpine` container to tar/untar the volume
contents to/from the host - no need to stop the volume's own container to
back it up. CI proves round-trip integrity: it writes a row into MySQL,
backs up the volume, destroys the container *and* the volume, restores from
the backup into a fresh volume, and asserts the row is still there.

## 5. Security hardening

- **Least privilege**: every service sets `cap_drop: [ALL]` and
  `security_opt: [no-new-privileges:true]`. MySQL adds back only the four
  capabilities its entrypoint actually needs on first boot
  (`CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETUID`/`SETGID`); everything else
  (app, nginx, loki, grafana) runs with zero added capabilities.
  Promtail is the one exception - it needs the Docker socket and host log
  directory to do its job, so it keeps default capabilities (documented here
  rather than silently dropped).
- **Non-root containers**: the app image runs as `spring`, nginx uses
  `nginxinc/nginx-unprivileged`.
- **Read-only root filesystem** on `app` and `nginx`, with `tmpfs` mounts for
  the few paths that genuinely need to be writable at runtime (`/tmp`,
  nginx's cache/run dirs).
- **Resource limits** (`mem_limit`, `cpus`) on every service, so one
  container can't starve the others.
- **Secret management**: MySQL passwords are never in plain env vars - they're
  Docker secrets (`secrets/*.txt`, bind-mounted read-only to `/run/secrets/…`
  inside the container). MySQL reads them via its built-in `_FILE` env var
  convention; Grafana via its own `__FILE` convention; the Spring Boot app
  via a small `entrypoint.sh` wrapper that reads the secret file into
  `DB_PASSWORD` before starting the JVM (falls back to a plain env var for
  the simple `docker run` demo case).
- **Image vulnerability scanning**: Trivy scans the built image for
  CRITICAL/HIGH CVEs in CI (`security-audit` job); results print to the job
  log.
- **Docker Bench for Security**: runs against the CI runner's Docker daemon
  and uploads the full report as a workflow artifact. Note: a shared,
  ephemeral GitHub Actions runner will always show more WARN/INFO findings
  than a dedicated, hardened host - the report is included as evidence the
  tool runs and produces real output, not as a claim that the CI runner
  itself is a hardened production host.

### Running the security tools yourself

```bash
# vulnerability scan
trivy image biradarvijay/docker-assignment2-app:latest

# host audit
docker run --rm --net host --pid host --userns host --cap-add audit_control \
  -v /var/lib:/var/lib:ro -v /var/run/docker.sock:/var/run/docker.sock:ro \
  -v /etc:/etc:ro --label docker_bench_security \
  docker/docker-bench-security
```

## 6. Bonus: log aggregation (Loki + Promtail + Grafana)

Promtail auto-discovers every container via the Docker daemon (no changes
needed in the app itself) and ships their stdout logs to Loki. Grafana comes
pre-provisioned with a Loki datasource and a dashboard
(`docker/grafana/provisioning/dashboards/app-logs.json`) showing a live tail
of the app's logs plus a log-volume-per-container panel.

```bash
docker compose up -d
# generate some traffic
curl http://localhost:8080/api/tasks
```

Open Grafana at http://localhost:3000 (user `admin`, password from
`secrets/grafana_admin_password.txt`) and the "Docker Assignment 2 - App
Logs" dashboard is there. Or query Loki directly:

```bash
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={compose_service="app"}'
```

CI's `compose-stack` job generates traffic and asserts Loki actually
ingested log lines tagged `compose_service="app"` before tearing down.

## 7. Setup & run

```bash
git clone https://github.com/bbiradarvijaykumar-nagarro/docker-assignment-2.git
cd docker-assignment-2

cp secrets/mysql_root_password.txt.example secrets/mysql_root_password.txt
cp secrets/mysql_password.txt.example secrets/mysql_password.txt
cp secrets/grafana_admin_password.txt.example secrets/grafana_admin_password.txt
# edit those three files with real passwords

cp .env.example .env

docker compose build
docker compose up -d
docker compose ps
```

- App (via nginx): http://localhost:8080
- Grafana: http://localhost:3000
- Loki API: http://localhost:3100

```bash
docker compose down -v   # tear down, including volumes
```

## 8. CI/CD pipeline

`.github/workflows/docker-ci.yml` runs on every push/PR to `main`:

1. **build-and-test** - Maven unit tests
2. **dockerfile-optimization** - builds `Dockerfile.before` and `Dockerfile`,
   compares image size and build time
3. **network-and-volume-demo** - manual `docker network`/`docker volume`
   lifecycle, plus a full backup/restore round trip with a data-integrity
   assertion
4. **security-audit** - Trivy vulnerability scan + Docker Bench for Security
5. **compose-stack** - brings up the whole stack, verifies the app through
   nginx, inspects the custom network, and asserts Loki ingested real logs
6. **push-and-pull** (main branch only) - pushes the optimized image to
   Docker Hub, removes it locally, pulls it back, and runs it standalone to
   prove the registry round trip

Enable job 6 by adding these repository secrets (Settings -> Secrets and
variables -> Actions):

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | your Docker Hub username |
| `DOCKERHUB_TOKEN` | a Docker Hub access token |
