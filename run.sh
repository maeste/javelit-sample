#!/bin/bash
# Run the llama-bench Analyzer app
# Requires: Java 21+, javelit-all.jar

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_CP="$SCRIPT_DIR/lib/pdfbox-3.0.4.jar:$SCRIPT_DIR/lib/fontbox-3.0.4.jar:$SCRIPT_DIR/lib/pdfbox-io-3.0.4.jar:$SCRIPT_DIR/lib/commons-logging-1.3.4.jar"

# Try javelit CLI first, then fall back to jar
if command -v javelit &> /dev/null; then
    cd "$SCRIPT_DIR" && javelit run App.java -cp "$LIB_CP"
elif [ -f /tmp/javelit.jar ]; then
    cd "$SCRIPT_DIR" && java -jar /tmp/javelit.jar run App.java -cp "$LIB_CP"
else
    echo "Downloading javelit..."
    curl -L -o /tmp/javelit.jar https://repo1.maven.org/maven2/io/javelit/javelit/0.86.0/javelit-0.86.0-all.jar
    cd "$SCRIPT_DIR" && java -jar /tmp/javelit.jar run App.java -cp "$LIB_CP"
fi
