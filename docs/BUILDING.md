# Building Margin

## What you need

- JDK 17 (Temurin or any distribution)
- Android SDK: platform 35, build-tools 35, platform-tools
- Gradle is not needed globally; the wrapper handles it

## From scratch on Windows

If the machine has no Android toolchain at all:

```powershell
$root = "$HOME\devtools"
New-Item -ItemType Directory -Force -Path "$root\dl" | Out-Null

# JDK 17
Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile "$root\dl\jdk17.zip"
Expand-Archive "$root\dl\jdk17.zip" -DestinationPath "$root\jdk-tmp"
Move-Item (Get-ChildItem "$root\jdk-tmp" -Directory)[0].FullName "$root\jdk17"

# Android command line tools
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" -OutFile "$root\dl\ct.zip"
Expand-Archive "$root\dl\ct.zip" -DestinationPath "$root\ct-tmp"
New-Item -ItemType Directory -Force -Path "$root\android-sdk\cmdline-tools" | Out-Null
Move-Item "$root\ct-tmp\cmdline-tools" "$root\android-sdk\cmdline-tools\latest"
```

Then accept the SDK licences and install the packages. This is a legal agreement with
Google, so read it before accepting:

```powershell
$env:JAVA_HOME = "$root\jdk17"
$env:ANDROID_HOME = "$root\android-sdk"
& "$root\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$root\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Point the project at the SDK by creating `local.properties` in the repository root. Use
forward slashes; a Windows path with backslashes is read as escape sequences by the Java
properties format and will silently break:

```properties
sdk.dir=C:/Users/you/devtools/android-sdk
```

## Building

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests, no device needed
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device
./gradlew :app:lintDebug              # lint
```

Outputs land in `app/build/outputs/apk/`.

## Release builds

A release build is minified and shrunk with R8, and needs a signing key. Create one:

```bash
keytool -genkey -v -keystore margin-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias margin
```

Then add `keystore.properties` to the repository root (it is gitignored):

```properties
storeFile=../margin-release.jks
storePassword=...
keyAlias=margin
keyPassword=...
```

```bash
./gradlew :app:assembleRelease
```

Without `keystore.properties` the release build still compiles but is unsigned, and cannot
be installed on a device.

## Installing on a phone

```bash
adb devices                                                   # confirm it is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the application id `com.margin.app.debug`, so it installs alongside a
release build rather than replacing it.

## Notes on the build configuration

- `kotlin.compiler.execution.strategy=in-process` is set in `gradle.properties`. The separate
  Kotlin compile daemon hung indefinitely during development on Windows; compiling inside the
  Gradle daemon costs some memory and is dependable. Remove it if your machine is happier
  with the default.
- The Gradle configuration cache is off for the same reason: it interacted badly with the
  above. It can be re-enabled with `org.gradle.configuration-cache=true`.
- `minSdk` is 26, which makes `java.time` available natively and avoids core library
  desugaring entirely.
