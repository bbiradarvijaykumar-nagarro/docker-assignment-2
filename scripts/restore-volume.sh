#!/bin/sh
# Restores a tar.gz produced by backup-volume.sh into a (possibly brand new)
# named volume. Wipes whatever is currently in the volume first, same
# disposable-container trick as the backup side.
#
# Usage: ./restore-volume.sh <volume-name> <backup-file>
set -e

VOLUME_NAME="${1:?usage: restore-volume.sh <volume-name> <backup-file>}"
BACKUP_FILE="${2:?usage: restore-volume.sh <volume-name> <backup-file>}"
BACKUP_DIR="$(cd "$(dirname "$BACKUP_FILE")" && pwd)"
BACKUP_BASENAME="$(basename "$BACKUP_FILE")"

docker volume create "${VOLUME_NAME}" >/dev/null

docker run --rm \
  -v "${VOLUME_NAME}:/volume" \
  -v "${BACKUP_DIR}:/backup:ro" \
  alpine \
  sh -c "rm -rf /volume/* && tar xzf /backup/${BACKUP_BASENAME} -C /volume"

echo "Restored ${BACKUP_FILE} into volume '${VOLUME_NAME}'"
