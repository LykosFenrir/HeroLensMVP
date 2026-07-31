#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
URL="https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"

if [ ! -f "$JAR" ]; then
  mkdir -p "$(dirname "$JAR")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$JAR"
  else
    echo "curl or wget is required for the one-time Gradle wrapper bootstrap." >&2
    exit 1
  fi
fi

if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$JAR" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$JAR" | awk '{print $1}')
else
  ACTUAL="$EXPECTED"
fi
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Gradle wrapper JAR checksum mismatch." >&2
  rm -f "$JAR"
  exit 1
fi

exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
