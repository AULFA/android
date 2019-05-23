#!/bin/sh

if [ -z "${NYPL_NEXUS_USER}" ]
then
  echo "error: NYPL_NEXUS_USER is not defined" 1>&2
  exit 1
fi

if [ -z "${NYPL_NEXUS_PASSWORD}" ]
then
  echo "error: NYPL_NEXUS_PASSWORD is not defined" 1>&2
  exit 1
fi

if [ -z "${LFA_BUILDS_USER}" ]
then
  echo "error: LFA_BUILDS_USER is not defined" 1>&2
  exit 1
fi

if [ -z "${LFA_BUILDS_PASSWORD}" ]
then
  echo "error: LFA_BUILDS_PASSWORD is not defined" 1>&2
  exit 1
fi

(cat <<EOF
org.librarysimplified.nexus.username=${NYPL_NEXUS_USER}
org.librarysimplified.nexus.password=${NYPL_NEXUS_PASSWORD}
EOF
) > gradle.properties.tmp || exit 1

mv gradle.properties.tmp gradle.properties || exit 1

mkdir -p simplified-app-lfa-offline/bundles || exit 1

PROJECT_DIR=$(pwd) || exit 1

cd simplified-app-lfa-offline/bundles || exit 1

wget \
  --timestamping \
  --user "${LFA_BUILDS_USER}" \
  --password "${LFA_BUILDS_PASSWORD}" \
  --no-if-modified-since \
  https://builds.lfa.one/auth/offline/offline.zip

cd "${PROJECT_DIR}" || exit 1

exec ./gradlew clean assembleDebug test
