# Ghar Khata

A household khata for milk, maid, newspaper and similar home services. Track deliveries and visits, and see what is owed. Your data is stored on your device, with no accounts. The ads-shipping build shows ads; Pro or the one-time Remove ads purchase hides them.

Package: `com.postpole.gharkhata`. This app was started from the Postpole Apps Android template (`Postpole-Apps/android-app-template`); see [docs/NEW_APP.md](docs/NEW_APP.md).

## What's inside
- Kotlin, Jetpack Compose (Material 3, light/dark/dynamic colour, edge-to-edge)
- Single activity, Navigation Compose, Hilt, Room (schemas exported to `app/schemas`), DataStore Preferences
- minSdk 26, compileSdk/targetSdk 36, version catalog in `gradle/libs.versions.toml`
- No paid APIs, servers, AI fees or paid databases. GA4 + Crashlytics are required; AD_ID / Ad ID collection stays ON (Google Ads)

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```
./gradlew assembleDebug testDebugUnitTest lint
./gradlew assembleRelease
```

## Build variants (ads switch)
One compile-time Gradle property, `adsShipping` (exposed as `BuildConfig.ADS_SHIPPING`), selects the variant. It works for every task, debug or release.

| Variant | Command | Behaviour |
| --- | --- | --- |
| Ads release (default) | `./gradlew bundleRelease` | UMP consent, Mobile Ads SDK, banner and interstitial, Remove ads row, "No ads" Pro bullet |
| Ad-free fallback | `./gradlew bundleRelease -PadsShipping=false` | No Mobile Ads SDK init (its auto-init provider is disabled in the manifest), no UMP, no ad requests or views, no Remove ads row, no ad-related copy (Privacy options row never appears). GA4, Crashlytics and AD_ID are unchanged |

Use the ad-free fallback if the ads release isn't ready by about 28 Nov. Release builds use production ad units; debug builds, and `-PtestAds=true` release builds, use Google's demo units only.

### UMP QA (debug builds only)
To make the consent form appear on a test device, add its hashed id (printed in Logcat as "Use RequestConfiguration.Builder().setTestDeviceIds(...)" / UMP debug messages) to the git-ignored `local.properties`:

```
ump.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,ANOTHERHASHEDID
```

Debug builds pass these ids to `ConsentDebugSettings`, and debug EEA geography is turned on automatically when ids are set. Override with `-PumpDebugEea=true|false`. Release builds never set debug geography or test devices (enforced in `consentDebugConfig`, covered by `ConsentDebugTest`). To re-test, clear app data or run `ConsentInformation.reset()`.

## Release and signing
`app/build.gradle.kts` signs the release build from `KEYSTORE_BASE64` (or `KEYSTORE_FILE`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (environment or `local.properties`); the build is unsigned if absent. The release workflow lives in `ci-templates/release.yml` (signed AAB artifact on push to main; optional draft upload to the Play internal track via `workflow_dispatch`).

## Analytics and crash reporting (required)
Google Analytics for Firebase (GA4) and Crashlytics are wired through the `Analytics` interface. Without `app/google-services.json` (git-ignored, supplied to CI as secret `GOOGLE_SERVICES_JSON`) the app builds with a no-op `Analytics`; with it, the google-services / crashlytics plugins apply automatically. Ad ID collection is intentionally left enabled for Google Ads. `privacy_policy_url` and `support_email` in `res/values/app_config.xml` are set to the real values.

Events: `app_open`, `first_launch`, `screen_view`, `pro_screen_view`, `purchase_started`, `purchase` (value + currency), `purchase_restored`, `backup_exported`, `backup_imported`.

## Backup
Settings > Back up to file / Restore from backup file use the Storage Access Framework (no storage permissions) and a versioned JSON format (`BackupRepository`). Android Auto Backup is off, so this is the only backup path.

## CI
The complete workflows are in `ci-templates/` (`ci.yml`, `release.yml`); a maintainer copies them to `.github/workflows/` (the bot cannot push there).
