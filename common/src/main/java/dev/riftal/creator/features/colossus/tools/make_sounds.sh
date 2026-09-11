#!/usr/bin/env bash
# Procedural placeholder audio for the creator_colossus feature (CONTRACT.md section 9.2).
#
# Minecraft plays Ogg *Vorbis*, and only applies 3D positional attenuation to MONO sounds -
# a stereo .ogg plays flat and non-directional. Every file below is `-ac 1` (mono) 44.1 kHz.
# The ffmpeg on this machine has no libvorbis and its native `vorbis` encoder is stereo-only,
# so ffmpeg renders mono WAV and oggenc (brew install vorbis-tools) encodes it - see ASSETS.md
# and CONTRACT.md section 9.2.
#
# Run from the repo root:
#   bash common/src/main/java/dev/riftal/creator/features/colossus/tools/make_sounds.sh
set -euo pipefail

command -v oggenc >/dev/null || { echo "oggenc not found; brew install vorbis-tools" >&2; exit 1; }

OUT="common/src/main/resources/assets/creator_colossus/sounds"
mkdir -p "$OUT/colossus" "$OUT/minion"

enc() { ffmpeg -v error -y -f lavfi -i "$1" -af "$2" -ac 1 -ar 44100 -f wav - | oggenc -Q -q 5 -o "$3" -; }

# colossus.roar - 1.75 s of brown noise pitched down, matching the 35-tick roar clip.
enc "anoisesrc=color=brown:duration=1.3:amplitude=0.9" \
    "atempo=0.75,lowpass=f=900,afade=t=in:st=0:d=0.1,afade=t=out:st=0.9:d=0.8,volume=1.1" \
    "$OUT/colossus/roar.ogg"

# colossus.swing - 0.5 s of filtered noise sweeping past: the arms coming round.
enc "anoisesrc=color=pink:duration=0.5:amplitude=0.7" \
    "highpass=f=300,afade=t=in:st=0:d=0.06,afade=t=out:st=0.18:d=0.3,volume=0.8" \
    "$OUT/colossus/swing.ogg"

# colossus.slam - 1.2 s low thud: a 48 Hz body with a noise crack on the transient.
enc "sine=frequency=48:duration=1.2" \
    "lowpass=f=200,afade=t=out:st=0.05:d=1.1,aecho=0.8:0.5:40:0.35,volume=1.4" \
    "$OUT/colossus/slam.ogg"

# colossus.step - 0.35 s short dull stomp.
enc "sine=frequency=70:duration=0.35" \
    "lowpass=f=300,afade=t=out:st=0.03:d=0.3,volume=1.0" \
    "$OUT/colossus/step.ogg"

# colossus.hurt - 0.6 s grinding stone: mid noise with a fast decay.
enc "anoisesrc=color=brown:duration=0.6:amplitude=0.8" \
    "atempo=0.9,bandpass=f=420:width_type=h:w=300,afade=t=out:st=0.15:d=0.45,volume=1.0" \
    "$OUT/colossus/hurt.ogg"

# colossus.death - 3.3 s collapse, just under the 70-tick death clip.
enc "anoisesrc=color=brown:duration=2.0:amplitude=0.9" \
    "atempo=0.6,lowpass=f=700,afade=t=in:st=0:d=0.1,afade=t=out:st=0.9:d=2.4,volume=1.2" \
    "$OUT/colossus/death.ogg"

# minion.hurt - 0.35 s dry crumble, brighter than the boss.
enc "anoisesrc=color=pink:duration=0.35:amplitude=0.7" \
    "bandpass=f=900:width_type=h:w=600,afade=t=out:st=0.08:d=0.27,volume=0.9" \
    "$OUT/minion/hurt.ogg"

# minion.death - 0.7 s of the same, falling apart.
enc "anoisesrc=color=pink:duration=0.7:amplitude=0.8" \
    "atempo=0.85,bandpass=f=700:width_type=h:w=700,afade=t=out:st=0.12:d=0.58,volume=1.0" \
    "$OUT/minion/death.ogg"

for f in "$OUT"/colossus/*.ogg "$OUT"/minion/*.ogg; do
  printf '%s  ' "$f"
  ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
          -show_entries format=duration -of default=nw=1:nk=1 "$f" | tr '\n' ' '
  printf '\n'
done
