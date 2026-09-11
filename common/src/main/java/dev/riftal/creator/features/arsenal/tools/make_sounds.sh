#!/usr/bin/env bash
# Regenerates the three placeholder sounds the arsenal feature ships.
#
#   bash common/src/main/java/dev/riftal/creator/features/arsenal/tools/make_sounds.sh
#
# ffmpeg only - no samples, no downloads. The ffmpeg on this machine has no libvorbis
# and its native vorbis encoder is stereo-only, so every file here is 2-channel and
# Minecraft therefore plays it non-positionally. The replacement assets must be
# mono 44.1 kHz. See ASSETS.md next to this folder.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../../../../../.." && pwd)"
out="$repo/common/src/main/resources/assets/creator_arsenal/sounds/arsenal"
mkdir -p "$out"

# Grapple hook bite: a short metallic click - noise transient plus a damped high ping.
ffmpeg -v error -y \
  -f lavfi -i "anoisesrc=color=white:duration=0.30:sample_rate=44100" \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*1350*t)*exp(-16*t)+0.25*sin(2*PI*2600*t)*exp(-26*t)':d=0.30:s=44100" \
  -filter_complex "[0:a]highpass=f=1800,volume=0.30,afade=t=out:st=0.02:d=0.10[n];[n][1:a]amix=inputs=2:duration=longest:normalize=0,volume=1.1,afade=t=out:st=0.20:d=0.10" \
  -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k "$out/hook_bite.ogg"

# Gravity Hammer slam: a low thud sliding 95 Hz -> 40 Hz with a dirt-burst tail.
ffmpeg -v error -y \
  -f lavfi -i "aevalsrc='0.7*sin(2*PI*t*(95-55*t))*exp(-3.2*t)':d=1.30:s=44100" \
  -f lavfi -i "anoisesrc=color=brown:duration=1.30:sample_rate=44100" \
  -filter_complex "[1:a]lowpass=f=900,volume=0.35,afade=t=out:st=0.05:d=0.55[n];[0:a][n]amix=inputs=2:duration=longest:normalize=0,volume=1.1,afade=t=out:st=1.0:d=0.30" \
  -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k "$out/slam_impact.ogg"

# Soul wisp arrival: a short bright rising blip.
ffmpeg -v error -y \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*t*(620+1500*t))+0.2*sin(2*PI*t*(1240+3000*t))':d=0.45:s=44100" \
  -af "afade=t=in:st=0:d=0.02,afade=t=out:st=0.28:d=0.17,volume=1.2" \
  -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k "$out/soul_absorb.ogg"

for f in "$out"/*.ogg; do
  ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
          -show_entries format=duration -of default=nw=1 "$f"
done
