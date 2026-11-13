#!/usr/bin/env bash
# Builds the release APK on this machine and prepares a GitHub draft release.
#
# APKs are always built locally, never by GitHub Actions (CI only runs tests).
# By default this script only builds and prints the commands to publish. It
# tags and publishes only with --publish, and only as a DRAFT release that you
# review and publish by hand on GitHub.
#
# Usage: scripts/release-local.sh <version> [--debug-apk] [--publish]
#   <version>    e.g. 1.0.1 (a leading "v" is accepted). Must match versionName
#                in app/build.gradle.kts.
#   --debug-apk  build assembleDebug instead of assembleRelease and mark the
#                release notes and title as a DEBUG BUILD. Use it only until a
#                release signing key exists.
#   --publish    create the annotated tag, push it, and create the DRAFT release.
#
# A release build needs a release keystore, configured in local.properties
# (git-ignored) with signing.storeFile, signing.storePassword, signing.keyAlias
# and signing.keyPassword (see "Release signing" in the README). This script
# never creates a keystore. Without one it stops and tells you so.
#
# Environment:
#   JAVA_HOME / ANDROID_HOME  default to Android Studio's JBR and
#                             ~/Library/Android/sdk when those exist.
#   RELEASE_SKIP_BRANCH_CHECK=1  skip the "on main, up to date with origin"
#                             check. For testing this script from a branch,
#                             worktree or copy. Refused together with --publish.
set -euo pipefail

die() {
  echo "error: $*" >&2
  exit 1
}

usage() {
  sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//' >&2
  exit 2
}

version=""
publish=0
debug_apk=0
for arg in "$@"; do
  case "$arg" in
    --publish) publish=1 ;;
    --debug-apk) debug_apk=1 ;;
    -h | --help) usage ;;
    -*) die "unknown option: $arg" ;;
    *)
      [ -z "$version" ] || die "only one version argument is allowed"
      version="${arg#v}"
      ;;
  esac
done
[ -n "$version" ] || usage
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.]+)?$ ]] || die "version '$version' is not X.Y.Z"
tag="v$version"

if [ "$publish" -eq 1 ] && [ "${RELEASE_SKIP_BRANCH_CHECK:-0}" = 1 ]; then
  die "--publish cannot be combined with RELEASE_SKIP_BRANCH_CHECK=1"
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || die "no JDK 17: set JAVA_HOME"
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] || die "no Android SDK: set ANDROID_HOME"
# Non-interactive shells (ssh) often lack the Homebrew prefix in PATH.
if [ -d /opt/homebrew/bin ]; then PATH="$PATH:/opt/homebrew/bin"; fi
for tool in git shasum; do
  command -v "$tool" >/dev/null || die "missing tool: $tool"
done
[ "$publish" -eq 0 ] || command -v gh >/dev/null || die "missing tool: gh"

# --- 1. Repository state ---------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
  die "working tree is not clean; commit or stash first"
fi
if [ "${RELEASE_SKIP_BRANCH_CHECK:-0}" != 1 ]; then
  [ "$(git rev-parse --abbrev-ref HEAD)" = main ] || die "not on branch main"
  git fetch --quiet origin main
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] ||
    die "main is not the same commit as origin/main; pull or push first"
else
  echo "WARNING: branch check skipped (RELEASE_SKIP_BRANCH_CHECK=1)" >&2
fi
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  die "tag $tag already exists locally"
fi
remote_status=0
git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1 || remote_status=$?
case "$remote_status" in
  0) die "tag $tag already exists on origin" ;;
  2) ;; # reachable, no such tag
  *) die "cannot reach origin to check for tag $tag" ;;
esac

# --- 2. Version consistency ------------------------------------------------
gradle_version="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
[ "$gradle_version" = "$version" ] ||
  die "versionName in app/build.gradle.kts is '$gradle_version', expected '$version'"
echo "Version $version matches versionName in app/build.gradle.kts"

# --- 3. Signing decision ---------------------------------------------------
# Reads a key from local.properties without executing it and without printing it.
prop() {
  [ -f local.properties ] || return 0
  sed -n "s/^$1[[:space:]]*=[[:space:]]*//p" local.properties | head -n 1
}
if [ "$debug_apk" -eq 1 ]; then
  task=assembleDebug
  apk_src="app/build/outputs/apk/debug/app-debug.apk"
  apk_name="open-kiosk-$version-DEBUG.apk"
  title="Open Kiosk $version (DEBUG BUILD)"
  echo "!!! DEBUG BUILD: signed with the debug key, not a release key."
else
  missing=""
  for key in signing.storeFile signing.storePassword signing.keyAlias signing.keyPassword; do
    [ -n "$(prop "$key")" ] || missing="$missing $key"
  done
  store_file="$(prop signing.storeFile)"
  if [ -z "$missing" ] && [ ! -f "app/$store_file" ]; then
    missing=" signing.storeFile (app/$store_file does not exist)"
  fi
  if [ -n "$missing" ]; then
    cat >&2 <<MSG
error: no release signing key is configured (problem in local.properties:$missing).

The release signing key decision is pending: a new release key, or publishing
the debug APK. This script never creates a keystore.
  - Once a key exists, put the four signing.* keys in local.properties
    (see "Release signing" in the README) and run this again.
  - To build the debug APK instead, run:
      scripts/release-local.sh $version --debug-apk
MSG
    exit 3
  fi
  task=assembleRelease
  apk_src="app/build/outputs/apk/release/app-release.apk"
  apk_name="open-kiosk-$version.apk"
  title="Open Kiosk $version"
fi

# --- 4. Tests and build ----------------------------------------------------
trap './gradlew --stop >/dev/null 2>&1 || true' EXIT
echo "==> Unit tests and lint"
./gradlew testDebugUnitTest lintDebug
rm -f "$apk_src"
echo "==> $task"
./gradlew "$task"
[ -f "$apk_src" ] || die "expected $apk_src after $task"

# --- 5. Checksum and notes -------------------------------------------------
out="build/release"
rm -rf "$out"
mkdir -p "$out"
cp "$apk_src" "$out/$apk_name"

apksigner="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f 2>/dev/null | sort | tail -n 1)"
if [ -n "$apksigner" ]; then
  echo "==> apksigner verify"
  "$apksigner" verify --print-certs "$out/$apk_name" | grep -E '^(Signer #1 certificate (DN|SHA-256 digest))' || true
  "$apksigner" verify "$out/$apk_name" >/dev/null
  echo "apksigner: signature verifies"
else
  echo "WARNING: apksigner not found, signature not verified" >&2
fi

(cd "$out" && shasum -a 256 "$apk_name" >SHA256SUMS)
echo "==> SHA256SUMS"
cat "$out/SHA256SUMS"

notes="$out/RELEASE_NOTES.md"
verify_line="Verify the download: \`shasum -a 256 -c SHA256SUMS\` (SHA256SUMS is attached to this release)."
if [ "$debug_apk" -eq 1 ]; then
  {
    echo "**DEBUG BUILD.** This APK is signed with the Android debug key, not a release"
    echo "key, and it is much larger than a release build (no R8 shrinking). It is"
    echo "published only because no release signing key exists yet. A debug APK cannot"
    echo "be updated in place by a later release APK: run \`adb uninstall com.openkiosk\`"
    echo "first."
    echo
    echo "Open Kiosk $version, built locally by the maintainer."
    echo
    echo "$verify_line"
  } >"$notes"
else
  {
    echo "Open Kiosk $version, built locally by the maintainer and signed with the release key."
    echo
    echo "$verify_line"
  } >"$notes"
fi

# --- 6. Tag and draft release ----------------------------------------------
tag_cmd=(git tag -a "$tag" -m "Open Kiosk $version")
push_cmd=(git push origin "$tag")
release_cmd=(gh release create "$tag" "$out/$apk_name" "$out/SHA256SUMS"
  --draft --verify-tag --title "$title" --notes-file "$notes")

echo
echo "Built: $out/$apk_name"
if [ "$publish" -eq 0 ]; then
  echo "Nothing was tagged or published. To publish, run these (or rerun with --publish):"
  echo "  $(printf '%q ' "${tag_cmd[@]}")"
  echo "  $(printf '%q ' "${push_cmd[@]}")"
  echo "  $(printf '%q ' "${release_cmd[@]}")"
  exit 0
fi

echo "==> Publishing (draft release)"
"${tag_cmd[@]}"
"${push_cmd[@]}"
"${release_cmd[@]}"
echo "Draft release created. Review it on GitHub and publish it by hand."
