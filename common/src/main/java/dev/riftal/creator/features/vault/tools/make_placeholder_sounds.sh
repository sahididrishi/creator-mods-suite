#!/usr/bin/env bash
# Regenerates every placeholder sound for the `vault` feature.
#
#   bash common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_sounds.sh
#
# The ffmpeg on this machine has no libvorbis, and ffmpeg's native `vorbis`
# encoder is stereo-only, hence `-ac 2 -c:a vorbis -strict -2`. That makes these
# placeholders NON-POSITIONAL in game (no distance attenuation) - the real
# replacement assets must be MONO 44.1 kHz. See ASSETS.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$(cd "$HERE/../../../../../../../../.." && pwd)/src/main/resources/assets/creator_vault/sounds"

gen() { # gen <relative/path.ogg> <lavfi source> <filter chain>
  local path="$OUT/$1"; shift
  local src="$1"; shift
  local filt="$1"; shift
  mkdir -p "$(dirname "$path")"
  ffmpeg -v error -y -f lavfi -i "$src" -af "$filt" \
         -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k "$path"
}

gen altar/activate.ogg  "sine=frequency=110:duration=1.6" "afade=t=in:st=0:d=0.1,afade=t=out:st=1.1:d=0.5,volume=0.55"
gen altar/unseal.ogg    "sine=frequency=180:duration=1.0" "atempo=0.9,afade=t=out:st=0.4:d=0.6,volume=0.5"
gen altar/reset.ogg     "sine=frequency=320:duration=0.7" "afade=t=out:st=0.3:d=0.4,volume=0.45"
gen chest/unseal.ogg    "anoisesrc=color=brown:duration=1.0" "highpass=f=180,afade=t=out:st=0.4:d=0.6,volume=0.6"
gen keeper/summon.ogg   "sine=frequency=70:duration=1.8"  "afade=t=in:st=0:d=0.2,afade=t=out:st=1.2:d=0.6,volume=0.7"
gen keeper/idle.ogg     "sine=frequency=95:duration=1.2"  "afade=t=in:st=0:d=0.3,afade=t=out:st=0.7:d=0.5,volume=0.35"
gen keeper/hurt.ogg     "anoisesrc=color=brown:duration=0.5" "highpass=f=300,afade=t=out:st=0.15:d=0.35,volume=0.6"
gen keeper/death.ogg    "sine=frequency=60:duration=2.0"  "afade=t=out:st=0.8:d=1.2,volume=0.65"

echo "--- verifying ---"
find "$OUT" -name '*.ogg' -print0 | while IFS= read -r -d '' f; do
  printf '%s: ' "${f#"$OUT/"}"
  ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
          -show_entries format=duration -of default=nw=1 "$f" | tr '\n' ' '
  echo
done
