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
- Local repo initialised from a source dump (no history before this commit).
- Remote: `origin` → https://github.com/mhmdmmm/GeneralsZH-Port.git (branch `main`).
- Push blocked until a GitHub token is stored (no `~/.git-credentials`, no `gh` CLI on this device).
  Store with:
  `git config --global credential.helper store` then
  `echo "https://mhmdmmm:<TOKEN>@github.com" >> ~/.git-credentials && chmod 600 ~/.git-credentials`
- Git identity set repo-locally: `mhmdmmm <mhmdmmm@users.noreply.github.com>`.
