#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo "usage: $0 input.mp4 output.mp4" >&2
  exit 2
fi
ffmpeg -y -i "$1" \
  -vf 'tpad=start=4:start_mode=clone:stop=4:stop_mode=clone,tmedian=radius=4' \
  -an -c:v libx264 -preset veryfast -crf 18 -pix_fmt yuv420p -movflags +faststart \
  "$2"
