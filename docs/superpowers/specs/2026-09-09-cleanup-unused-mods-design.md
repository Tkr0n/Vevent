# Design: Cleanup Unused Mods in Launcher

## Problem
The launcher downloads mods from the manifest but never removes mods that are no longer listed. Over time, obsolete `.jar` files accumulate in the mods directory, potentially causing conflicts or loading outdated versions.

## Solution
Add a cleanup step in `GameInstallerService.EnsureInstalledAsync` that removes all `.jar` files from the mods directory that are NOT listed in the manifest's `clientMods` array, before downloading new mods.

## How it works

### Cleanup logic (in `GameInstallerService.EnsureInstalledAsync`)
1. Build a `HashSet<string>` of expected mod filenames from `clientMods`:
   - For mods with `FileName` set: add `FileName` directly
   - For mods with `ModrinthProjectId` (no local `.jar`): resolve `FileName` via `ModrinthService.ResolveFileAsync` and add it
2. Scan all `.jar` files in `modsDir`
3. Delete each `.jar` whose filename is not in the HashSet
4. Proceed with normal download (only downloads if file doesn't exist)

### What gets cleaned
- Third-party mods from `client-mods/mods/` that were removed from the manifest
- Custom mods that were removed from the manifest
- Any orphaned `.jar` files

### What does NOT get cleaned
- Non-`.jar` files (configs, resource packs, etc.)
- Mods that are still in the manifest

### Error handling
- Individual file deletion errors are caught and ignored (same pattern as existing `verkku-title` cleanup)
- Modrinth resolution errors are caught; if resolution fails, the mod is skipped during cleanup but still downloaded normally

## Files to modify
- `launcher/src/VerkkuCraftLauncher/services/GameInstallerService.cs` — add cleanup step before download loop

## Testing
- Verify that after implementation, running the launcher with a manifest removes extra `.jar` files from the mods directory
- Verify that mods listed in the manifest are NOT deleted
- Verify that non-`.jar` files are untouched
