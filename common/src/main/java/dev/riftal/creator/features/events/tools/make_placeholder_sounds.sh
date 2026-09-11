#!/usr/bin/env bash
# Regenerates every placeholder sound for the `events` feature.
#
#   bash common/src/main/java/dev/riftal/creator/features/events/tools/make_placeholder_sounds.sh
#
# Requires ffmpeg and oggenc (`brew install vorbis-tools`).
#
# Every file MUST come out MONO 44.1 kHz: Minecraft applies 3D attenuation and
# panning only to mono sounds, so a stereo ogg plays flat and non-directional no
# matter how the code positions it (CONTRACT.md 9.2). The ffmpeg on this machine
# has no libvorbis and its native `vorbis` encoder refuses anything but 2
# channels, so ffmpeg synthesises a mono WAV and oggenc does the encoding. Do
# not "simplify" this back to a single ffmpeg call with `-ac 2`.
#
# Each file is synthesised to be plausible for its role rather than a beep:
# the drone and the hum are slow low sines with a slight beat, the whistle is a
# falling sweep, the impact is a decaying sub thud with noise, the horn is two
# notes, the pop is a fast bright blip.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$(cd "$HERE/../../../../../../../../.." && pwd)/src/main/resources/assets/creator_events/sounds"

gen() { # gen <relative/path.ogg> <lavfi source> <filter chain>
  local path="$OUT/$1"; shift
  local src="$1"; shift
  local filt="$1"; shift
  local wav; wav="$(mktemp -t creator_events_sound).wav"
  mkdir -p "$(dirname "$path")"
  ffmpeg -v error -y -f lavfi -i "$src" -af "$filt" \
         -ac 1 -ar 44100 -c:a pcm_s16le "$wav"
  oggenc -Q -q 5 -o "$path" "$wav"
  rm -f "$wav"
}

# Blood moon: a 55 Hz drone with a fifth above it and a slow beat, topped and
# tailed so the client-side loop does not click at the seam.
gen bloodmoon/drone.ogg \
  "aevalsrc='0.45*sin(2*PI*55*t)+0.17*sin(2*PI*82.5*t)+0.09*sin(2*PI*110*t)*(0.5+0.5*sin(2*PI*0.3*t))':d=6:s=44100" \
  "lowpass=f=900,afade=t=in:st=0:d=0.9,afade=t=out:st=5.1:d=0.9,volume=0.8"

# Meteor whistle: a falling howl with air noise behind it.
gen meteor/whistle.ogg \
  "aevalsrc='0.55*sin(2*PI*(1500-360*t)*t)+0.12*(random(0)-0.5)':d=3:s=44100" \
  "highpass=f=300,afade=t=in:st=0:d=0.15,afade=t=out:st=2.3:d=0.7,volume=0.7"

# Meteor impact: a sub thud plus a debris burst, both decaying fast.
gen meteor/impact.ogg \
  "aevalsrc='(0.9*sin(2*PI*42*t)+0.35*sin(2*PI*63*t)+0.55*(random(0)-0.5))*exp(-3.2*t)':d=2.2:s=44100" \
  "lowpass=f=1800,afade=t=out:st=1.6:d=0.6,volume=0.9"

# Siege horn: two notes, D2 then G2, with a harmonic each.
gen siege/horn.ogg \
  "aevalsrc='(0.5*sin(2*PI*if(lt(t,1.0),73.4,98)*t)+0.3*sin(2*PI*if(lt(t,1.0),146.8,196)*t)+0.12*sin(2*PI*if(lt(t,1.0),220.2,294)*t))*(1-0.25*sin(2*PI*3*t))':d=2.4:s=44100" \
  "lowpass=f=2600,afade=t=in:st=0:d=0.08,afade=t=out:st=1.9:d=0.5,volume=0.75"

# Lucky pop: a short bright rising blip.
gen lucky/pop.ogg \
  "aevalsrc='0.6*sin(2*PI*(900+1600*t)*t)*exp(-9*t)':d=0.45:s=44100" \
  "highpass=f=400,afade=t=out:st=0.35:d=0.1,volume=0.8"

# Void hum: sub-bass with a slow tremolo.
gen void/hum.ogg \
  "aevalsrc='0.5*sin(2*PI*38*t)*(0.7+0.3*sin(2*PI*0.8*t))+0.12*sin(2*PI*57*t)':d=4:s=44100" \
  "lowpass=f=600,afade=t=in:st=0:d=0.6,afade=t=out:st=3.3:d=0.7,volume=0.85"

echo "--- verifying ---"
find "$OUT" -name '*.ogg' -print0 | while IFS= read -r -d '' f; do
  printf '%s: ' "${f#"$OUT/"}"
  ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
          -show_entries format=duration -of default=nw=1 "$f" | tr '\n' ' '
  echo
  channels="$(ffprobe -v error -show_entries stream=channels -of csv=p=0 "$f")"
  if [ "$channels" != "1" ]; then
    echo "FAIL: $f is $channels-channel; Minecraft needs mono" >&2
    exit 1
  fi
done
