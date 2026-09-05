#!/bin/sh
set -eu
mkdir -p build/classes
javac -d build/classes $(find src/main/java -name '*.java' -print)
java -cp build/classes org.nonprofit.mail.DomainOnboarding "$@"
