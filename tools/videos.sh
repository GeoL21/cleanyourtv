#!/usr/bin/env bash
# This fork: push a folder of background videos (.mp4, .mkv, .webm) to the launcher, which plays them muted and
# looping behind Home (VideoWallpaper). The folder's videos replace the ones on the TV; an empty folder clears them.
# Names may use only letters, digits, dot, dash and underscore. Rotation follows the theme's
# wallpaper_rotation_minutes; video_dim and video_wallpaper are theme keys too (see FORK.md).
#
# Usage: TV_HOST=192.168.1.50 tools/videos.sh ~/Videos/fondos-tv
set -euo pipefail
SRC="${1:?usage: videos.sh <folder with videos>}"
SRC="$(cd "$SRC" && pwd)"
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 ~/Videos/fondos-tv}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-video
tv() { "$PY" -I tv.py "$@"; }
RESTART="a=\$(settings get secure enabled_accessibility_services); am force-stop $PKG; case \"\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)\" in $PKG/*) am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null ;; *) am start -n $PKG/.MainActivity >/dev/null ;; esac; case \"\$a\" in *$PKG*) sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

tv "mkdir -p $DIR/videos 2>/dev/null; content delete --uri $URI/videos 2>/dev/null; rm -f $DIR/videos/* 2>/dev/null; echo cleared" | tail -n 1
COUNT=0
for VIDEO in "$SRC"/*; do
  [ -f "$VIDEO" ] || continue
  case "$VIDEO" in
    *.[mM][pP]4|*.[mM][kK][vV]|*.[wW][eE][bB][mM]) ;;
    *) continue ;;
  esac
  NAME=$(basename "$VIDEO")
  [[ "$NAME" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "rename it with only letters, digits, . - _ : $NAME" >&2; exit 1; }
  echo "sending $NAME ($(du -h "$VIDEO" | cut -f1))..."
  tv --push "$VIDEO" "$TMP-$NAME" >/dev/null
  # Through the launcher's provider first, so the file belongs to the launcher (see theme.sh); cp as the fallback.
  tv "rm -f $DIR/videos/$NAME 2>/dev/null; { o=\$(content write --uri $URI/videos/$NAME < $TMP-$NAME 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $TMP-$NAME $DIR/videos/$NAME; rm -f $TMP-$NAME" >/dev/null
  COUNT=$((COUNT + 1))
done
tv "$RESTART; echo restarted" | tail -n 1
echo "$COUNT video(s) on the TV."
