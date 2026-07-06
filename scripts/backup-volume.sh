#!/bin/sh
# Backs up a named Docker volume to a tar.gz on the host, without needing the
# volume's own container to be stopped - a disposable alpine container just
# mounts the volume read-only and the host backup dir, then tars it.
#
# Usage: ./backup-volume.sh <volume-name> [backup-dir]
set -e

VOLUME_NAME="${1:?usage: backup-volume.sh <volume-name> [backup-dir]}"
BACKUP_DIR="${2:-$(pwd)/backups}"
TIMESTAMP=$(date +%Y%m%d%H%M%S)

mkdir -p "$BACKUP_DIR"

docker run --rm \
  -v "${VOLUME_NAME}:/volume:ro" \
  -v "${BACKUP_DIR}:/backup" \
  alpine \
  tar czf "/backup/${VOLUME_NAME}-${TIMESTAMP}.tar.gz" -C /volume .

echo "Backed up '${VOLUME_NAME}' to ${BACKUP_DIR}/${VOLUME_NAME}-${TIMESTAMP}.tar.gz"
