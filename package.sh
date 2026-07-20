#!/bin/bash
# NetDebugger jpackage build script
# JDK 17+ with jpackage required

set -e

JDK_HOME="C:/Users/Heawill/.jdks/ms-17.0.19"
JPACKAGE="$JDK_HOME/bin/jpackage"
JAVA="$JDK_HOME/bin/java"
MVN="mvn"

echo "========================================="
echo " NetDebugger - jpackage App-Image builder"
echo "========================================="
echo "JDK: $JDK_HOME"
echo "Java version:"
"$JAVA" -version 2>&1

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
INPUT_DIR="$PROJECT_DIR/target"
OUTPUT_DIR="$PROJECT_DIR/installer-output"

echo ""
echo "Step 1/3: Building jar with Maven..."
echo ""
export JAVA_HOME="$JDK_HOME"
"$MVN" install -DskipTests

echo ""
echo "Step 2/3: Preparing input (jar + runtimes)..."
echo ""

# Copy runtimes into target/ so jpackage bundles them alongside the jar
if [ -d "$PROJECT_DIR/runtimes" ]; then
  cp -r "$PROJECT_DIR/runtimes" "$INPUT_DIR/"
  echo "Runtimes copied into target/"
fi

# Clean previous output
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

echo ""
echo "Step 3/3: Building app-image (self-contained portable directory)..."
echo "Input:  $INPUT_DIR"
echo "Output: $OUTPUT_DIR"
echo ""

"$JPACKAGE" \
  --type app-image \
  --name "NetDebugger" \
  --app-version "1.0.0" \
  --vendor "DebugTool" \
  --description "TCP/UDP Debug Tool with JCEF Chromium" \
  --input "$INPUT_DIR" \
  --main-jar "tcp-udp-debug-tool-1.0.0.jar" \
  --main-class "com.debugtool.App" \
  --dest "$OUTPUT_DIR" \
  --icon "$PROJECT_DIR/src/main/resources/logo/icon.ico" \
  --java-options "-Xms128m" \
  --java-options "-Xmx512m"

# Clean up runtimes copied into target
rm -rf "$INPUT_DIR/runtimes"

echo ""
echo "========================================="
echo " Done! App-image created."
echo " Location: $OUTPUT_DIR/NetDebugger/"
echo " Run: $OUTPUT_DIR/NetDebugger/NetDebugger.exe"
echo "========================================="
ls -lh "$OUTPUT_DIR/NetDebugger/"*.exe 2>/dev/null || true
echo ""
du -sh "$OUTPUT_DIR/NetDebugger/" 2>/dev/null || true
