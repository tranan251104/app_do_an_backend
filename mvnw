#!/bin/sh
set -e
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.11
DIST="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MVN="$DIST/apache-maven-$MAVEN_VERSION/bin/mvn"
if [ ! -x "$MVN" ]; then
  mkdir -p "$DIST"
  ZIP="$DIST/maven.zip"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$ZIP"; else wget -q "$URL" -O "$ZIP"; fi
  unzip -q -o "$ZIP" -d "$DIST"
fi
exec "$MVN" -f "$BASE_DIR/pom.xml" "$@"
