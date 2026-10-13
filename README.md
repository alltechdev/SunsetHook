# SunsetHook

An LSPosed / Xposed module that keeps the **classic Google Assistant** working
on the Google app, instead of being sunset into Gemini onboarding.

Google is phasing out the classic Assistant and routing users into Gemini. If
you deliberately stay on an older Google app build, a server-side "Sunset" flag
and silent Play Store updates can still take Assistant away from you. SunsetHook
pins you in place. It also restores the ability to set up and use classic Google
Assistant on a device where Gemini has already taken over: the setup flow is
still the genuine Gemini setup, but with the Sunset flag forced off you end up on
classic Google Assistant once that setup finishes.

## What it does

The module has two scopes:

- **`com.google.android.googlequicksearchbox` (the Google app)**: forces the
  Assistant "Sunset" feature flag off, so opted-out users are no longer routed
  into Gemini onboarding. The flag is read through an obfuscated static helper
  (`hcvl.L(hbhk)`, flag `45798734`). These obfuscated names are specific to
  **Google app 17.59.18**, which ships the same `classes.dex` on every
  architecture (arm64-v8a, armeabi-v7a, x86_64). The hook is gated on the
  `versionName` starting with `17.59.18`, so it runs on all of those builds and
  **skips any other version** rather than risk hooking the wrong method.

- **`android` (system_server)**: refuses new install sessions for the Google
  app unless they come from root (uid 0) or shell (uid 2000). This stops the
  Play Store from silently replacing your pinned build. Manual updates via
  `su`/`adb` still work, so you stay in control.

## Requirements

- A rooted device with **LSPosed** or **Vector** (or another Xposed framework, API 82+).
- The Google app pinned at **17.59.18** (any architecture: arm64-v8a,
  armeabi-v7a, or x86_64). On any other version the flag hook no-ops (check
  `logcat` for `SunsetHook:` lines).
- Scopes to enable in LSPosed: **Google app** and **System Framework**
  (`android`). A reboot after enabling is recommended.

## Building

Built entirely on-device with Termux. You need `javac`, `d8`, `aapt2`,
`zipalign`, `apksigner`, and apktool's framework at
`~/.local/share/apktool/framework/1.apk` (run `apktool` once to create it).

First, generate a signing key (not committed to the repo). Choose your own
password:

```bash
export KEYSTORE_PASSWORD='your-keystore-password'
keytool -genkeypair -keystore key.jks -alias ars18 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=US"
```

Then build (the same `KEYSTORE_PASSWORD` must be exported):

```bash
export KEYSTORE_PASSWORD='your-keystore-password'
./build.sh
```

This produces `SunsetHook.apk`, ready to install.

### Building in CI

`.github/workflows/build.yml` builds and signs the APK on every push and
uploads it as a workflow artifact. Pushing a `v*` tag also attaches the APK to
a GitHub release. The workflow needs these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of your `key.jks` (`base64 -w0 key.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (e.g. `ars18`) |
| `KEY_PASSWORD` | key password |

## Installing

```bash
adb install SunsetHook.apk   # or install the APK on-device
```

Then enable the module in LSPosed, select the two scopes above, and reboot.

Setup note: when you go through Assistant setup, the voice setup screen will
show the Gemini setup flow even when you choose Google. That is expected. With
the module active and the Sunset flag forced off, classic Google Assistant
becomes the active assistant once you finish setup.

If Gemini is already set up as your assistant, forcing the flag off does not
switch you back on its own. Clear the Google app's data (Settings > Apps >
Google > Storage > Clear data), then reopen it and run Assistant setup again.
With the flag forced off, setup now lands on classic Google Assistant.

The classic Assistant setup wizard is buried once Gemini is active. You can
open it directly from a root shell:

```bash
su -c "am start -n com.google.android.googlequicksearchbox/com.google.android.apps.gsa.staticplugins.opa.setupwizard.SetupWizardActivity"
```

That activity name is specific to Google app 17.59.18.

## How it works

See [`src/com/atd/sunsethook/Hook.java`](src/com/atd/sunsethook/Hook.java)
for the hook logic. `ForceFalse` replaces the flag read with `false`;
`BlockGsaInstall` rejects non-root/shell install sessions for the Google app.

The `stubs/` directory contains compile-time-only stubs of the Xposed API; they
are not bundled into the APK (the real implementation is provided by the Xposed
framework at runtime).

## Notes & caveats

- Obfuscated symbol names change between Google app versions. This module is
  intentionally tied to the 17.59.18 version (all architectures share its
  `classes.dex`) and will not adapt itself to other versions.
- This project is not affiliated with or endorsed by Google. "Google",
  "Google Assistant", and "Gemini" are trademarks of Google LLC. The Google app
  itself is not distributed here.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
