# Contributing to Strata

There are two applications. `app/` is the original Compose launcher with optional Arch/PRoot and Wayland support. `system/companion/` is the Strata Android app, using Termux and optional Shizuku. Changes to one are not automatically shipped in the other.

Use JDK 25 to run the Gradle wrapper, with a JDK 17 toolchain available for OWL. The original app compiles against Android 37; the preview compiles against Android 36. Both require Android 10 or later. Configure `ANDROID_HOME` with your SDK path.

```sh
./gradlew :owl:test :app:testDebugUnitTest :owl:owlJar
./gradlew -p system/companion assembleDebug testDebugUnitTest lintDebug
git diff --check
```

The original application's full APK additionally needs its native dependencies; follow `scripts/fetch-proot.sh`. These are not part of the preview. Do not substitute an incomplete original APK for the preview artifact.

Report the application ID, version, exact phone/Android/ROM build, steps, expected result, actual result and whether the issue occurs with a personal, work, clone or private profile. Redact accounts, notification content, tokens and personal paths from logs. A passing JVM test does not establish physical touch, IME, OEM service or profile compatibility.

Preserve user data, existing configuration and existing Android. Device checks for this fork use normal APK installation; they must not involve flashing, bootloader unlocking, factory resets, rooting or changing Android system packages. Commands that require root must report their actual privileges. Do not replace a missing sandbox with a command runner that pretends isolation exists.

PRs should name the affected application(s), the concrete before/after behavior, relevant upstream issue/PR, tests run, and device checks still outstanding. Consult [the upstream audit](docs/UPSTREAM_AUDIT.md) before importing another PR: several proposed patches overlap or target older preference storage.
