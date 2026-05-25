# Fynlo External Dependency Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** How to vet, add, update, and remove third-party libraries.

---

## 0. Why this document exists

Every dependency is:
- A potential security vulnerability
- A potential breaking change waiting to happen
- A privacy concern (some libraries phone home)
- A licensing question (GPL contamination of closed code)
- A maintenance burden (someone has to update it)

The KSP2 bug that blocked baseline profile generation (`KspAATask.kt:772 InvalidPathException`) is a textbook example: a dependency I trust broke in a non-obvious way, blocking shipping. Govern dependencies like you govern code.

---

## 1. Dependency inventory

Maintained in `app/build.gradle.kts` + `gradle/libs.versions.toml`. Current state (top-level):

| Category | Examples |
|---|---|
| Build | AGP 9.2.1, Kotlin 2.2.10, JDK 17, KSP 2.3.7 |
| DI | Hilt |
| Persistence | Room |
| Backend | Firebase BoM, Firestore, Auth, Crashlytics, Analytics |
| UI | Compose BoM, Material3 |
| Async | Coroutines, Flow |
| Testing | JUnit 4, Espresso, Compose UI Test |
| Macrobenchmark | androidx.benchmark |

Refresh this list each time `libs.versions.toml` changes substantially.

---

## 2. Adding a new dependency

Before `implementation("group:artifact:version")` lands in `build.gradle.kts`:

### 2.1 Justify
Answer in PR description:
- Why this dependency? What does the app gain?
- What's the alternative? (Can you write it yourself in <100 lines?)
- Is there already a similar dep that could do the job?

### 2.2 Vet

Check:
- [ ] **License compatible** with closed-source Android app distribution (MIT, Apache 2.0, BSD = OK; GPL = NOT OK; LGPL = caution)
- [ ] **Active maintenance** — last commit within 6 months? Issues responded to?
- [ ] **Reputation** — has the maintainer's name been associated with any past incidents?
- [ ] **Star/usage count** — not the only criterion, but very low usage on a critical dep is a red flag
- [ ] **Transitive deps** — does this bring in 50 other libraries? Inspect `./gradlew :app:dependencies | grep [name]`
- [ ] **Privacy footprint** — does it collect telemetry? Does its docs mention user data?
- [ ] **Size impact** — run R8/ProGuard analysis; how much does APK grow?

### 2.3 Pin

Always pin exact version, not ranges:
```kotlin
// BAD
implementation("com.example:lib:[1.0,2.0)")
implementation("com.example:lib:1.+")

// GOOD
implementation("com.example:lib:1.2.3")
```

Range/floating versions allow surprise updates that can break or compromise. Pin and review updates manually.

### 2.4 Document

Add to `gradle/libs.versions.toml` with comment if non-obvious:
```toml
# Used by FeatureX for parsing format Y. Alternative: custom parser ~80 LoC.
[libraries]
fast-parser = { module = "com.example:fast-parser", version.ref = "fast-parser" }
```

### 2.5 Test

Run full test suite + manual smoke test after adding.

---

## 3. Updating dependencies

### 3.1 Security patches

Within **7 days** of disclosure for:
- Firebase / Google Play Services (security advisories)
- Kotlin compiler (security advisories)
- AGP (security advisories)
- Any dep with a published CVE affecting Fynlo's usage

### 3.2 Minor / patch versions (1.2.3 → 1.2.4 or 1.2.3 → 1.3.0)

Monthly review:
- Check `gradle/libs.versions.toml` against latest
- Update versions one-by-one (not bulk)
- Run tests after each
- Ship in normal release cycle

### 3.3 Major versions (1.x → 2.x)

Quarterly review:
- Major versions often have breaking changes
- Read the changelog/migration guide first
- Do this in a dedicated PR, not bundled with feature work
- Allocate at least a day for fallout

### 3.4 Renovate / Dependabot

When ready (post-Sprint 2): enable Dependabot for automated PRs on dependency updates. Configure to:
- Group minor + patch updates weekly
- Major versions as individual PRs
- Auto-merge security patches after CI passes (caveat: requires high test confidence)

---

## 4. Removing dependencies

If a dep is no longer used:
1. Grep entire codebase for any reference
2. Remove from `libs.versions.toml` and `build.gradle.kts`
3. Run full build + tests
4. Document removal in PR

Periodic cleanup: every 6 months, scan for unused deps using `./gradlew :app:dependencies --refresh-dependencies` and manual review.

---

## 5. The KSP2 lesson

Real example to remember: KSP 2.x had a bug (`InvalidPathException`) that blocked baseline profile generation. Symptoms:
- Build succeeds
- Tests pass
- One specific Gradle task (`generateProdBaselineProfile`) fails with cryptic Windows path error
- Workaround: skip the task in CI, document the issue

Lesson: even well-known deps can have non-obvious bugs. When this happens:
1. Search GitHub issues for the dep — often someone hit the same problem
2. Document the issue in `PROJECT_STATE_FOR_AI.md` (§5.3 already has KSP2 workaround)
3. Don't downgrade silently; document the version pin reason
4. Watch for upstream fix; update when stable

---

## 6. Supply chain security

### 6.1 Signing

Verify Maven artifacts are signed when downloading. Gradle does this by default for `mavenCentral()`. Don't disable signature verification.

### 6.2 Repository sources

Only use these repos:
- `mavenCentral()`
- `google()`
- `gradlePluginPortal()`

Don't add random Maven repos without due diligence. Custom repos = trust extension.

### 6.3 Lockfiles

Use Gradle dependency locking for production builds:
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    // ...
}

// app/build.gradle.kts
dependencyLocking {
    lockAllConfigurations()
}
```

Commit lockfiles. Verify on CI that lockfile matches resolution.

### 6.4 Known-bad list

If a CVE is disclosed for a Fynlo-used dep:
- Add to `dependency-cves.md` in repo
- Track until fixed or dep replaced
- Don't ship a release with open critical CVEs unless mitigated

---

## 7. License compliance

Indian copyright law applies. For mobile apps distributed via Play Store:

- **MIT / BSD / Apache 2.0** — permissive; require attribution; OK
- **LGPL** — caution; dynamic linking OK, static linking requires source disclosure
- **GPL** — viral; do not use; would force open-sourcing of Fynlo
- **Proprietary / Commercial** — read license carefully; pay if required
- **No license stated** — do not use; ambiguous = uncopyable

Generate an attributions screen in-app listing all open-source deps with their licenses. Tools:
- `./gradlew :app:licensee` (if Licensee plugin added) — generates JSON of all licenses
- Update About → Open Source Licenses screen on every dep change

---

## 8. Forbidden patterns

Never:
- Vendor someone's source code (copy-paste) without preserving license + attribution
- Use a "warez" or pirated SDK
- Modify a dependency's source in-place without forking
- Bundle dependencies that ship their own analytics back to a third party
- Use deps that require server-side licensing checks (security & privacy concerns)

---

## 9. Cross-references

- `PROJECT_STATE_FOR_AI.md` §5 — environment + KSP2 known issue
- `LINT_RULES.md` — code-level rules
- `LEGAL_PROTOCOL.md` — license + IP implications
- `PRIVACY_PROTOCOL.md` §7 — third-party SDK introduction

---

## 10. The one rule

**Every dependency is a liability. Vet before adding. Pin always. Update deliberately. Remove what you don't use.**

---

**End of EXTERNAL_DEPENDENCY_PROTOCOL.md v1.0**
