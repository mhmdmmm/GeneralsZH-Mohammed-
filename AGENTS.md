# GeneralsZH Android Port — project memory

## What it is
Android launcher/port wrapper for **Command & Conquer: Generals — Zero Hour** (GeneralsX project).
Package `com.generalsx.zerohour`, namespace same, compileSdk 35, minSdk 28 (deliberately not lowered
to 24 — see comment in `app/build.gradle`). UI/launcher Java only; the game engine is native
(DXVK 2.6 needs Vulkan 1.3, so the real gate is the GPU driver, not the OS version).

## Layout
- `app/src/main/java/com/generalsx/zerohour/` — launcher activities: `SetupActivity` (biggest, ~164K,
  setup/asset-copy flow), `GeneralsZHActivity` (main menu), `GeneralsOnlineActivity` +
  `GeneralsOnlineSession`, `ModsActivity`, `LogViewerActivity`, `NetworkDiagnosticsActivity`,
  `ReplayCheckActivity`, `FolderPickerActivity`, `DataPackInstaller`, `LocaleHelper`, `InsetUtil`,
  `NetworkTrace`, `SupportLinks`.
- `app/src/main/assets/gamedata/` — shipped game data (e.g. `Window/GroupPanel.wnd`).
- `app/src/main/res/values-*/strings.xml` — many locales (ar, fa, ru, uk, ko, pt-rBR, …).
- `patches/` — engine patches.
- `app/debug.keystore` — **committed on purpose**: fixed debug key so CI builds are installable as
  updates over each other (fresh runners otherwise generate a random key per run). Debug-only.

## Build
- No Gradle wrapper in the tree (`gradlew` absent) — builds are expected to run on CI
  (GitHub Actions) or with a system Gradle + Android SDK. Not buildable in plain Termux.
- Useful local check when SDK exists: `gradle :app:assembleDebug`, `gradle :app:lintDebug`.

## Repo / remote (2026-10-30)
- Local repo initialised from a source dump; history starts at `f08a0ba` (73 files).
- Remote: `origin` → https://github.com/mhmdmmm/GeneralsZH-Port.git (branch `main`, tracking set).
- Repo created via API as **public**; pushes work — token is stored in `~/.git-credentials`
  (`credential.helper=store`, `chmod 600`), no `gh` CLI on this device. Never commit the token.
- Git identity set repo-locally: `mhmdmmm <mhmdmmm@users.noreply.github.com>`.
- Note: `git config --global http.version` was mis-set to `HTTP/1.` (invalid, caused a warning);
  fixed to `HTTP/1.1` on 2026-10-30.
- No CI workflow in the tree yet (`.github/workflows/` absent) — builds currently need a runner
  added before Actions can produce APKs.
## UI / theming (2026-10-30)
- There is **no `res/layout/` directory**: every launcher screen is built in Java
  (`SetupActivity.buildUi()`, `UiKit`). `GeneralsZHActivity` is the SDL3 game surface, not a menu.
- The whole look comes from `res/values/colors.xml` (design tokens) + `res/values/themes.xml`
  (M3 theme `Theme.GeneralsZHSettings`, applied per-Activity in the manifest). Changing a token
  repaints every screen; no Java edit needed for a colour change.
- Active theme (maintainer's pick, 30/10/2026): **blood red on black** — ground `#0A0A0B`,
  primary `#E5484D`, charcoal surface ladder, muted salmon `gzh_tertiary` for destructive actions.
  Accent pairs checked for WCAG AA (on_primary/primary 4.7:1, primary/ground 5.1:1,
  faint/container_high 5.8:1). Previous palettes: lavender M3 (#0E0E11), navy-teal + gold (#0F2230).
- Titles: `app_name` = "Generals ZH - Mohammed" (home-screen label; long names ellipsize in the
  launcher but the full string shows in Settings), `setup_title` = "Generals: Zero Hour"
  (app-bar hero title), `setup_window_title` = "Zero Hour Settings" (localized per locale).
  Any string change must land in **all 13 `values-*/strings.xml`** in the same commit.
- App icon = adaptive `mipmap-anydpi-v26/ic_launcher.xml` -> `drawable/ic_launcher_background`
  (shape colour, currently `#0A0A0B`) + `mipmap-xxxhdpi/ic_launcher_foreground.png` (432x432 eagle,
  unchanged). To install custom art, overwrite that PNG keeping art inside the centre 264x264;
  minSdk 28 > 26, so no other densities are needed. Launchers cache icons: reinstall to see a swap.
- **Mods screen (reinstated 30/10/2026)**: `.ModsActivity` (`ModsActivity.java`, theme
  `Theme.GeneralsZHSettings`, `exported=false`) lists the `*.big` files in
  `<sdcard>/GeneralsMods` (created on first open) as a checkbox list with file sizes, a
  "MODS SELECTED n/m" counter, REFRESH, CLOSE, and APPLY MODS (placeholder Toast — enabling a
  `.big` means changing engine INI/load order in native code, not copying a file).
  - 12 `mods_*` strings in all 13 locales; colours from `colors.xml` only.
  - Storage: `READ/WRITE_EXTERNAL_STORAGE` (maxSdkVersion 29) granted at runtime for Android
    9/10; on 11+ the screen deep-links to `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`
    (`MANAGE_EXTERNAL_STORAGE` is already declared) and re-checks in `onResume()`.
  - Selection is keyed by file name and pruned on every reload, so a deleted file never
    stays "selected".
  - Reached from the **MODS** overlay in `GeneralsZHActivity.onCreate()` — injected *after*
    `super.onCreate()` (SDLActivity calls `setContentView()` there and would throw the view
    away), bottom-left, `#99000000` fill + `#FFD700` text, wrapped in try/catch.
  - Verification on the phone is static only (no JDK/SDK): brace/paren balance, every `R.*`
    reference resolved against `res/`, and every XML parsed with ElementTree. Real proof is
    the CI APK.

## Publishing from the phone (2026-10-30)
- `~/GeneralsZH-Android-Port-main` is **not** a git repo; `android/` is a nested repo that pushes to
  `mhmdmmm/GeneralsZH-Port` (Gradle shell only, **no workflow** -> pushing there builds nothing).
- The buildable tree is `mhmdmmm/GeneralsZH-Android-Port` (full engine, at v1.4.0). Its `android/`
  matched this snapshot except the theme edits, verified by git blob SHA via the API.
- `python ~/zh_publish.py` = the push path: hidden token prompt -> saves `~/.git-credentials`
  (mode 600) -> blob-diffs `android/` against the remote tree -> one commit through the Git Data API
  (no clone of the 18 GB repo on the phone) -> push to `main` still triggers `build-apk.yml`.
  Token needs `repo` + `workflow` scopes (the commit adds a workflow file). `--dry-run` works with
  no token and prints the exact file plan. `DELETE_PATHS` is empty — a push only adds/updates.
- XML gotcha (bit us twice): no `--` anywhere inside an XML comment, not even as a dash — Android
  `aapt2` (and ElementTree) reject the whole resource/manifest. Validate every XML you touch.
- `build-apk.yml` (added 30/10/2026) is the only APK path (owner has no PC): push to main/tags ->
  DXVK fork + NDK/SDK + vcpkg -> `package-android-zh.sh` -> artifact
  `GeneralsXZH-android-run<N>.apk`, 20-45 min. It needs no repository secrets.
