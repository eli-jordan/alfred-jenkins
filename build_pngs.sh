#!/bin/bash

# Requires 'brew install librsvg'

# To re-run when new SVGs are added
#   ./build_pngs.sh svgs alfred/icons

# The source directory that holds SVG files
SVGS_DIR="$1"

# The target directory where the generated png files should be written
PNGS_DIR="$2"

mkdir -p "${PNGS_DIR}"

for svgFile in "${SVGS_DIR}/"*.svg ; do
  filename=$(basename "$svgFile")
  name=${filename%.*}
  rsvg-convert -h 256 "$svgFile" > "${PNGS_DIR}/${name}.png"
done
