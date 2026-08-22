#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
DIST_DIR="$BASE_DIR/.mvn/wrapper/dists/apache-maven-3.9.16"
MAVEN_BIN="$DIST_DIR/apache-maven-3.9.16/bin/mvn"

property() {
    sed -n "s/^$1=//p" "$PROPS" | tail -n 1
}

sha512() {
    if command -v sha512sum >/dev/null 2>&1; then
        sha512sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 512 "$1" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha512 "$1" | awk '{print $NF}'
    else
        echo "mvnw: no SHA-512 utility found (sha512sum, shasum, or openssl required)" >&2
        exit 1
    fi
}

if [ ! -x "$MAVEN_BIN" ]; then
    URL=$(property distributionUrl)
    EXPECTED=$(property distributionSha512Sum)
    ARCHIVE="$DIST_DIR/apache-maven-3.9.16-bin.zip"
    mkdir -p "$DIST_DIR"

    echo "mvnw: bootstrapping Apache Maven 3.9.16" >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 -o "$ARCHIVE" "$URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" "$URL"
    else
        echo "mvnw: curl or wget is required to bootstrap Maven" >&2
        exit 1
    fi

    ACTUAL=$(sha512 "$ARCHIVE")
    if [ "$ACTUAL" != "$EXPECTED" ]; then
        rm -f "$ARCHIVE"
        echo "mvnw: Maven distribution SHA-512 mismatch" >&2
        exit 1
    fi

    EXTRACT="$DIST_DIR/.extract"
    rm -rf "$EXTRACT"
    mkdir -p "$EXTRACT"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ARCHIVE" -d "$EXTRACT"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
        (cd "$EXTRACT" && "$JAVA_HOME/bin/jar" xf "$ARCHIVE")
    elif command -v jar >/dev/null 2>&1; then
        (cd "$EXTRACT" && jar xf "$ARCHIVE")
    else
        echo "mvnw: unzip or the JDK jar tool is required to unpack Maven" >&2
        exit 1
    fi
    rm -rf "$DIST_DIR/apache-maven-3.9.16"
    mv "$EXTRACT/apache-maven-3.9.16" "$DIST_DIR/apache-maven-3.9.16"
    rm -rf "$EXTRACT" "$ARCHIVE"
fi

exec "$MAVEN_BIN" "$@"
