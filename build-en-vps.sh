#!/usr/bin/env bash
# Compila la APK de depuracion en un servidor Ubuntu (por ejemplo tu VPS).
# Uso:
#   chmod +x build-en-vps.sh
#   ./build-en-vps.sh
# Resultado: app/build/outputs/apk/debug/app-debug.apk
set -euo pipefail

SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
GRADLE_VERSION="8.7"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

echo "==> Paquetes base"
sudo apt-get update -y
sudo apt-get install -y openjdk-17-jdk unzip wget

export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
echo "JAVA_HOME=$JAVA_HOME"

echo "==> Android SDK command-line tools"
mkdir -p "$SDK_DIR/cmdline-tools"
if [ ! -d "$SDK_DIR/cmdline-tools/latest" ]; then
  cd "$SDK_DIR/cmdline-tools"
  wget -q "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -O tools.zip
  unzip -q tools.zip
  mv cmdline-tools latest
  rm tools.zip
fi
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

echo "==> Aceptar licencias e instalar plataformas"
yes | sdkmanager --licenses > /dev/null
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "==> Gradle $GRADLE_VERSION"
if ! command -v gradle > /dev/null || ! gradle --version | grep -q "$GRADLE_VERSION"; then
  wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O /tmp/gradle.zip
  sudo rm -rf "/opt/gradle-${GRADLE_VERSION}"
  sudo unzip -q -o /tmp/gradle.zip -d /opt
  export PATH="/opt/gradle-${GRADLE_VERSION}/bin:$PATH"
fi

echo "==> Compilando"
cd "$(dirname "$0")"
gradle assembleDebug --stacktrace --no-daemon

echo
echo "APK generada en:"
echo "  $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
