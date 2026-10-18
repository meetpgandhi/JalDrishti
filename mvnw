#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Startup Script for JalDrishti
# ----------------------------------------------------------------------------

set -e

DIRNAME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_PROPERTIES="$DIRNAME/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$WRAPPER_PROPERTIES" ]; then
  DIST_URL="$(grep '^distributionUrl=' "$WRAPPER_PROPERTIES" | cut -d= -f2-)"
else
  echo "Error: $WRAPPER_PROPERTIES not found" >&2
  exit 1
fi

MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
MAVEN_ZIP_NAME="$(basename "$DIST_URL")"
MAVEN_DIR_NAME="${MAVEN_ZIP_NAME%-bin.zip}"
WRAPPER_DIR="$DIRNAME/.mvn/wrapper/dist"
MAVEN_HOME="$WRAPPER_DIR/$MAVEN_DIR_NAME"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$WRAPPER_DIR"
  ZIP_FILE="$WRAPPER_DIR/$MAVEN_ZIP_NAME"
  echo "Downloading Maven from $DIST_URL ..."
  curl -sSL "$DIST_URL" -o "$ZIP_FILE"
  echo "Unpacking Maven ..."
  unzip -q -o "$ZIP_FILE" -d "$WRAPPER_DIR"
  rm -f "$ZIP_FILE"
  chmod +x "$MAVEN_HOME/bin/mvn"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
