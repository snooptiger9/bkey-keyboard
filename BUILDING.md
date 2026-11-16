# 🛠️ Building bkey from source

### Requirements
- JDK 17
- Android SDK (compileSdk 34, minSdk 24, targetSdk 34)
- Git

### 1. Clone the repo
```bash
git clone https://github.com/snooptiger9/bkey-keyboard.git
cd bkey-keyboard
```

### 2. Create your own signing key
The real release keystore is **not** in this repo (it's gitignored, like any private signing key should be). To build, generate your own debug key:

```bash
keytool -genkeypair -v -keystore app/bkey_release.keystore \
  -alias bkey -keyalg RSA -keysize 2048 -validity 10000
```

Then create `app/keystore.properties`:
```properties
storePassword=your_store_password
keyAlias=bkey
keyPassword=your_key_password
```

(Use whatever password you set with `keytool` above.)

### 3. Build
There are two product flavors — **lite** and **full** (full adds the OTP/SMS-reading feature) — and the usual debug/release build types.

```bash
./gradlew assembleLiteDebug      # quickest way to get a runnable APK
./gradlew assembleFullRelease    # signed release build, full flavor
```

APKs land in `app/build/outputs/apk/<flavor>/<buildType>/`.

### Notes
- A debug-signed APK from your own key will **not** be recognized as an update to an existing install signed with the official key — you'd need to uninstall the old one first.
- CI (`.github/workflows/build.yml`) builds with JDK 17 and `./gradlew assembleRelease` the same way, using its own secret-stored keystore.
