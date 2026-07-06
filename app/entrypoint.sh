#!/bin/sh
set -e

# Spring Boot has no native support for Docker's _FILE secret convention, so
# this small wrapper bridges the two: if a secret file is mounted at
# /run/secrets/mysql_password, its content wins over the plain DB_PASSWORD
# env var (which is still supported for the plain "docker run" / local demo).
if [ -f /run/secrets/mysql_password ]; then
    DB_PASSWORD="$(cat /run/secrets/mysql_password)"
    export DB_PASSWORD
fi

exec java -jar app.jar
