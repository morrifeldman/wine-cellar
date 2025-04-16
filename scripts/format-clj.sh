#!/bin/bash

# Format a Clojure file using zprint
# Usage: ./format-clj.sh <file>

if [ $# -ne 1 ]; then
  echo "Usage: $0 <file>"
  exit 1
fi

FILE=$1

# Check if the file exists
if [ ! -f "$FILE" ]; then
  echo "File not found: $FILE"
  exit 1
fi

# Check if the file is a Clojure file
if [[ ! "$FILE" =~ \.(clj|cljs|cljc)$ ]]; then
  echo "Not a Clojure file: $FILE"
  exit 1
fi

echo "Formatting $FILE"

# Check if command-line zprint is available
if command -v zprint &> /dev/null; then
  echo "Using command-line zprint"
  zprint '{:style :community :map {:comma? false} :width 80}' < "$FILE" > "$FILE.tmp"
else
  echo "Using Clojure-based zprint (installing command-line zprint could speed up this step)"
  cd "$(dirname "$0")/.." && clojure -M:zprint '{:style :community :map {:comma? false} :width 80}' < "$FILE" > "$FILE.tmp"
fi

# Check if zprint succeeded
if [ $? -ne 0 ]; then
  echo "Error formatting file: $FILE"
  rm -f "$FILE.tmp"
  exit 1
fi

# Replace the original file with the formatted one
mv "$FILE.tmp" "$FILE"
echo "Formatted: $FILE"
