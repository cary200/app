#!/bin/sh
set -eu
cd "$(dirname "$0")"
jar=gradle/wrapper/gradle-wrapper.jar
hash=498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17
verify() {
    if command -v sha256sum >/dev/null; then printf '%s  %s\n' "$hash" "$1" | sha256sum -c - >/dev/null
    else printf '%s  %s\n' "$hash" "$1" | shasum -a 256 -c - >/dev/null; fi
}
if [ ! -f "$jar" ]; then
    tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
    curl -fsSL --retry 2 https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar -o "$tmp"
    verify "$tmp"; cp "$tmp" "$jar"
fi
verify "$jar"
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$jar" org.gradle.wrapper.GradleWrapperMain "$@"
