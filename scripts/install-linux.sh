#!/usr/bin/env bash
# Installs Kebab Editor for the current user, or reinstalls it over an existing install.
#
#   scripts/install-linux.sh              build and (re)install
#   scripts/install-linux.sh --uninstall  remove an install
#   --icon=NAME                           the icon to install: default, gruvbox or tela
#
# Installs (no root needed):
#   ~/.local/share/kebab-editor/                          the application (Gradle installDist)
#   ~/.local/bin/kebab-editor                             a link to its launcher
#   ~/.local/share/applications/kebab-editor.desktop      the menu / dock entry
#   ~/.local/share/icons/hicolor/scalable/apps/kebab-editor.svg
#
# The app needs Java 25 or newer at run time: JAVA_HOME if set, else `java` on PATH.
set -euo pipefail

APP_ID="kebab-editor"
APP_NAME="Kebab Editor"
MIN_JAVA=25

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
APP_DIR="$DATA_HOME/$APP_ID"
BIN_LINK="$HOME/.local/bin/$APP_ID"
DESKTOP_FILE="$DATA_HOME/applications/$APP_ID.desktop"
ICON_FILE="$DATA_HOME/icons/hicolor/scalable/apps/$APP_ID.svg"
BUILD_DIR="$REPO/app/build/install/$APP_ID"
LOGO_DIR="$REPO/editor/src/main/resources/img"

logo_for() {
    case "$1" in
        default) echo "$LOGO_DIR/logo.svg" ;;
        gruvbox) echo "$LOGO_DIR/logo_gruvbox.svg" ;;
        tela)    echo "$LOGO_DIR/logo_tela.svg" ;;
        *)       return 1 ;;
    esac
}

say()  { printf '==> %s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

refresh_caches() {
    if command -v update-desktop-database >/dev/null; then
        update-desktop-database -q "$DATA_HOME/applications" 2>/dev/null || true
    fi
    if command -v gtk-update-icon-cache >/dev/null && [ -f "$DATA_HOME/icons/hicolor/index.theme" ]; then
        gtk-update-icon-cache -q -f -t "$DATA_HOME/icons/hicolor" 2>/dev/null || true
    fi
    # Desktops that watch the folder pick up a changed timestamp without a cache.
    touch "$DATA_HOME/icons/hicolor" 2>/dev/null || true
}

# Only ever delete a folder that holds this app's launcher, never an unrelated one.
remove_app_dir() {
    if [ -e "$APP_DIR" ]; then
        [ -x "$APP_DIR/bin/$APP_ID" ] || die "$APP_DIR exists but is not a $APP_NAME install; not touching it"
        rm -rf "$APP_DIR"
    fi
}

remove_bin_link() {
    if [ -L "$BIN_LINK" ]; then
        rm -f "$BIN_LINK"
    elif [ -e "$BIN_LINK" ]; then
        warn "$BIN_LINK is not a link made by this script; left as it is"
    fi
}

uninstall() {
    say "Removing $APP_NAME"
    remove_app_dir
    remove_bin_link
    rm -f "$DESKTOP_FILE" "$ICON_FILE"
    refresh_caches
    say "Removed"
}

check_java() {
    local java
    if [ -n "${JAVA_HOME:-}" ]; then
        java="$JAVA_HOME/bin/java"
    else
        java="$(command -v java || true)"
    fi
    [ -n "$java" ] && [ -x "$java" ] || die "Java $MIN_JAVA+ is needed to run $APP_NAME, but no java was found (set JAVA_HOME or install a JDK)"

    # `java -version` prints e.g.: openjdk version "25.0.4" 2026-08-18
    local version major
    version="$("$java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)"
    major="${version%%[.+-]*}"
    [[ "$major" =~ ^[0-9]+$ ]] || die "could not read the Java version from $java"
    if [ "$major" -lt "$MIN_JAVA" ]; then
        die "$APP_NAME needs Java $MIN_JAVA+, but $java is Java $version"
    fi
    say "Using Java $version ($java)"
}

write_desktop_file() {
    mkdir -p "$(dirname "$DESKTOP_FILE")"
    # StartupWMClass matches the window class the app sets (AppIdentity), so the dock shows this icon.
    cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Version=1.5
Name=$APP_NAME
GenericName=Level Editor
Comment=3D level editor with typed archetypes, saving to glTF
Exec="$APP_DIR/bin/$APP_ID"
TryExec=$APP_DIR/bin/$APP_ID
Icon=$APP_ID
Terminal=false
Categories=Graphics;3DGraphics;
Keywords=level;editor;gltf;3d;greybox;archetype;
StartupNotify=true
StartupWMClass=$APP_ID
EOF
    if command -v desktop-file-validate >/dev/null; then
        desktop-file-validate "$DESKTOP_FILE" || warn "desktop-file-validate reported problems"
    fi
}

install() {
    [ -f "$LOGO" ] || die "logo not found at $LOGO"
    check_java

    # Build before touching the old install, so a failed build leaves it working.
    say "Building"
    (cd "$REPO" && ./gradlew --quiet :app:installDist)
    [ -x "$BUILD_DIR/bin/$APP_ID" ] || die "build did not produce $BUILD_DIR/bin/$APP_ID"

    if [ -e "$APP_DIR" ]; then
        say "Replacing the existing install in $APP_DIR"
    else
        say "Installing to $APP_DIR"
    fi
    remove_app_dir
    mkdir -p "$(dirname "$APP_DIR")"
    cp -r "$BUILD_DIR" "$APP_DIR"

    mkdir -p "$(dirname "$BIN_LINK")"
    remove_bin_link
    [ -e "$BIN_LINK" ] || ln -s "$APP_DIR/bin/$APP_ID" "$BIN_LINK"

    mkdir -p "$(dirname "$ICON_FILE")"
    cp "$LOGO" "$ICON_FILE"

    write_desktop_file
    refresh_caches

    say "Installed with the $ICON icon. Start $APP_NAME from the application menu, or run: $APP_ID"
    case ":$PATH:" in
        *":$HOME/.local/bin:"*) ;;
        *) warn "$HOME/.local/bin is not on your PATH, so '$APP_ID' only works from the menu" ;;
    esac
}

ACTION="install"
ICON="default"

while [ $# -gt 0 ]; do
    case "$1" in
        --uninstall) ACTION="uninstall" ;;
        --icon=*)    ICON="${1#--icon=}" ;;
        --icon)      [ $# -ge 2 ] || die "--icon needs a name: default, gruvbox or tela"; ICON="$2"; shift ;;
        -h|--help)   ACTION="help" ;;
        *)           die "unknown option '$1' (use --help)" ;;
    esac
    shift
done

LOGO="$(logo_for "$ICON")" || die "unknown icon '$ICON' (use default, gruvbox or tela)"

case "$ACTION" in
    install)   install ;;
    uninstall) uninstall ;;
    help)      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
