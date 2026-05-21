# Dev flavor — Firebase setup

The `dev` product flavor builds `app.fynlo.dev`, which installs side-by-side
with the production `app.fynlo` so you can test against throwaway data without
risking real records.

## ⚠️ Current state: placeholder (NOT isolated yet)

`google-services.json` in this folder is a **placeholder** that points at the
**same** Firebase project as production (`com-example-cashmemo-37d57`). It only
exists so the `dev` variant compiles. Cloud features (Google Sign-In, Firestore
sync) will NOT work for the dev build until you complete the steps below —
local Room storage works fine.

Until then, **do not assume dev is isolated from prod**.

## To get true dev/prod isolation

1. Firebase Console → create a **new project**, e.g. `fynlo-dev`.
2. In that project, **add an Android app** with package name `app.fynlo.dev`.
3. Add the **debug keystore SHA-1** (and the dev release SHA-1 if you sign dev
   builds) so Google Sign-In works:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
4. Download that project's `google-services.json` and **replace this file** with it.
5. Apply the same `firestore.rules` to the new project (Firebase Console →
   Firestore → Rules, or `firebase deploy --only firestore:rules -P fynlo-dev`).

After that, `assembleDevDebug` talks to `fynlo-dev` and `assembleProdRelease`
talks to the live project. Wiping dev can never touch prod data.

## Build commands

| Goal | Command | Output |
|------|---------|--------|
| Dev debug (daily work) | `./gradlew assembleDevDebug` | `app-dev-debug.apk` |
| Prod release AAB (Play) | `./gradlew bundleProdRelease` | `bundle/prodRelease/app-prod-release.aab` |
| Prod release APK | `./gradlew assembleProdRelease` | `apk/prod/release/app-prod-release.apk` |

Note: plain `assembleRelease` / `bundleRelease` now build **both** flavors —
prefer the flavor-qualified task names above.
