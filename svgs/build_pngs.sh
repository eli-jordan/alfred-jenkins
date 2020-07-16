#!/bin/bash

# Requires 'brew install librsvg'

mkdir png

for i in *.svg ; do
  rsvg-convert -h 256 "$i"> "png/$i".png
done
