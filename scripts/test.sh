#!/bin/sh
set -eu
mkdir -p build/test-classes
javac -d build/test-classes $(find src/main/java src/test/java -name '*.java' -print)
java -cp build/test-classes org.nonprofit.mail.DomainReleasePolicyTest
