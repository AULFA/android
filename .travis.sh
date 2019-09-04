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

if [ -z "${LFA_BUILDS_SSH_KEY}" ]
then
  echo "LFA_BUILDS_SSH_KEY not set"
  exit 1
fi

if [ -z "${LFA_KEYSTORE_PASSWORD}" ]
then
  echo "LFA_KEYSTORE_PASSWORD not set"
  exit 1
fi

#------------------------------------------------------------------------
# Configure SSH

mkdir -p "${HOME}/.ssh" || exit 1
echo "${LFA_BUILDS_SSH_KEY}" | base64 -d > "${HOME}/.ssh/id_ed25519" || exit 1
chmod 700 "${HOME}/.ssh" || exit 1
chmod 600 "${HOME}/.ssh/id_ed25519" || exit 1

(cat <<EOF
[builds.lfa.one]:1022 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIH/vroEIxH46lW/xg+CmCDwO7FHN24oP+ad4T/OtB/D2
EOF
) >> "$HOME/.ssh/known_hosts" || exit 1

#------------------------------------------------------------------------
# Configure Nexus and keystore

scp -P 1022 travis-ci@builds.lfa.one:lfa-keystore.jks .

(cat <<EOF

nexusUsername=${NYPL_NEXUS_USER}
nexusPassword=${NYPL_NEXUS_PASSWORD}

au.org.libraryforall.keyAlias=main
au.org.libraryforall.keyPassword=${LFA_KEYSTORE_PASSWORD}
au.org.libraryforall.storePassword=${LFA_KEYSTORE_PASSWORD}
EOF
) >> gradle.properties || exit 1

#------------------------------------------------------------------------
# Configure bundled credentials

scp -P 1022 travis-ci@builds.lfa.one:online-app-credentials.json .
scp -P 1022 travis-ci@builds.lfa.one:bugsnag.conf .

cp online-app-credentials.json simplified-app-lfa/src/main/assets/account_bundled_credentials.json
cp online-app-credentials.json simplified-app-lfa-offline/src/main/assets/account_bundled_credentials.json
cp online-app-credentials.json simplified-app-lfa-laos/src/main/assets/account_bundled_credentials.json

cp bugsnag.conf simplified-app-lfa/src/main/assets/bugsnag.conf
cp bugsnag.conf simplified-app-lfa-offline/src/main/assets/bugsnag.conf
cp bugsnag.conf simplified-app-lfa-laos/src/main/assets/bugsnag.conf

#------------------------------------------------------------------------
# Configure offline bundles

mkdir -p simplified-app-lfa-laos/bundles || exit 1
mkdir -p simplified-app-lfa-offline/bundles || exit 1
mkdir -p simplified-app-lfa/bundles || exit 1

wget \
  --timestamping \
  --user "${LFA_BUILDS_USER}" \
  --password "${LFA_BUILDS_PASSWORD}" \
  --no-verbose \
  --output-document=simplified-app-lfa-offline/bundles/offline.zip \
  https://builds.lfa.one/auth/offline/offline.zip

wget \
  --timestamping \
  --user "${LFA_BUILDS_USER}" \
  --password "${LFA_BUILDS_PASSWORD}" \
  --no-verbose \
  --output-document=simplified-app-lfa-laos/bundles/offline-laos.zip \
  https://builds.lfa.one/auth/offline-laos/offline-laos.zip

wget \
  --timestamping \
  --user "${LFA_BUILDS_USER}" \
  --password "${LFA_BUILDS_PASSWORD}" \
  --no-verbose \
  --output-document=simplified-app-lfa/bundles/offline.zip \
  https://builds.lfa.one/auth/offline-online/offline-online.zip

#------------------------------------------------------------------------
# Build!

./gradlew clean assemble test

#------------------------------------------------------------------------
# Publish APKs

scp -P 1022 ./simplified-app-lfa-offline/build/outputs/apk/release/*.apk travis-ci@builds.lfa.one:/sites/builds.lfa.one/apk/
scp -P 1022 ./simplified-app-lfa/build/outputs/apk/release/*.apk travis-ci@builds.lfa.one:/sites/builds.lfa.one/apk/
scp -P 1022 ./simplified-app-lfa-laos/build/outputs/apk/release/*.apk travis-ci@builds.lfa.one:/sites/builds.lfa.one/apk/

