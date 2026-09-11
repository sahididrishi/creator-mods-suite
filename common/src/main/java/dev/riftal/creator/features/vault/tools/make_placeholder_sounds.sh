#!/usr/bin/env bash
# Regenerates every placeholder sound for the `vault` feature.
#
#   bash common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_sounds.sh
#
# MONO 44.1 kHz Ogg Vorbis, every file, no exceptions. Minecraft applies 3D
# distance attenuation and panning ONLY to mono sounds, so a stereo .ogg plays
# flat and non-directional however carefully the code positions it - the altar
# would be as loud across the vault as standing on it. CONTRACT.md section 9.2.
#
# ffmpeg cannot write the file on its own here: this machine's ffmpeg has no
# libvorbis, and ffmpeg's NATIVE vorbis encoder refuses anything but 2 channels
# ("Current FFmpeg Vorbis encoder only supports 2 channels"). So ffmpeg
# synthesises a mono WAV and oggenc (vorbis-tools, `brew install vorbis-tools`)
# does the encoding. This is the pipeline that produced the committed files; the
# verification pass at the bottom fails the script if any of them comes out
# stereo.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$(cd "$HERE/../../../../../../../../.." && pwd)/src/main/resources/assets/creator_vault/sounds"

command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
command -v oggenc >/dev/null || { echo "oggenc not found - brew install vorbis-tools"; exit 1; }

gen() { # gen <relative/path.ogg> <lavfi source> <filter chain>
  local path="$OUT/$1"; shift
  local src="$1"; shift
  local filt="$1"; shift
  mkdir -p "$(dirname "$path")"
  # Step 1: synthesise to MONO 44.1 kHz PCM. Step 2: encode with oggenc, which is
  # the half of this that can actually write 1-channel Vorbis.
  ffmpeg -v error -y -f lavfi -i "$src" -af "$filt" \
         -ac 1 -ar 44100 -c:a pcm_s16le -f wav - \
    | oggenc -Q -q 5 -o "$path" -
}

gen altar/activate.ogg  "sine=frequency=110:duration=1.6" "afade=t=in:st=0:d=0.1,afade=t=out:st=1.1:d=0.5,volume=0.55"
gen altar/unseal.ogg    "sine=frequency=180:duration=1.0" "atempo=0.9,afade=t=out:st=0.4:d=0.6,volume=0.5"
gen altar/reset.ogg     "sine=frequency=320:duration=0.7" "afade=t=out:st=0.3:d=0.4,volume=0.45"
gen chest/unseal.ogg    "anoisesrc=color=brown:duration=1.0" "highpass=f=180,afade=t=out:st=0.4:d=0.6,volume=0.6"
gen keeper/summon.ogg   "sine=frequency=70:duration=1.8"  "afade=t=in:st=0:d=0.2,afade=t=out:st=1.2:d=0.6,volume=0.7"
gen keeper/idle.ogg     "sine=frequency=95:duration=1.2"  "afade=t=in:st=0:d=0.3,afade=t=out:st=0.7:d=0.5,volume=0.35"
gen keeper/hurt.ogg     "anoisesrc=color=brown:duration=0.5" "highpass=f=300,afade=t=out:st=0.15:d=0.35,volume=0.6"
gen keeper/death.ogg    "sine=frequency=60:duration=2.0"  "afade=t=out:st=0.8:d=1.2,volume=0.65"

echo "--- verifying (channels MUST be 1) ---"
status=0
while IFS= read -r -d '' f; do
  info="$(ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
                  -show_entries format=duration -of default=nw=1 "$f" | tr '\n' ' ')"
  printf '%s: %s\n' "${f#"$OUT/"}" "$info"
  case "$info" in
    *codec_name=vorbis*) ;;
    *) echo "  ^^ NOT Ogg Vorbis"; status=1 ;;
  esac
  case "$info" in
    *channels=1*) ;;
    *) echo "  ^^ NOT MONO - this sound would have no distance attenuation in game"; status=1 ;;
  esac
done < <(find "$OUT" -name '*.ogg' -print0 | sort -z)
exit "$status"
