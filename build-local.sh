#!/bin/sh
# Builds build/lt3dconverter.jar (needs JDK 17+)
set -e
mkdir -p build/classes
javac --release 17 -d build/classes $(find src/main -name '*.java')
jar --create --file build/lt3dconverter.jar --main-class lt3d.App -C build/classes .
echo "OK: build/lt3dconverter.jar"
