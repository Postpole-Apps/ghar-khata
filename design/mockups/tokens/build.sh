#!/usr/bin/env bash
# Rebuilds ../html/styles.css = header + generated colour tokens + shape/type/gradient tokens + components + shapes.
set -e; cd "$(dirname "$0")"
[ -d node_modules ] || npm install --silent
node gen-tokens.mjs > colors.css
cat header.css colors.css components.css shapes.css > ../html/styles.css
echo "wrote html/styles.css"
