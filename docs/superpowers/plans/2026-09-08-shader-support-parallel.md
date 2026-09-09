# Soporte de Shaders - Ejecución en Paralelo

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan.

**Goal:** Add complete shader support to VerkkuCraft Launcher

**Architecture:** Parallel execution with dependency phases

---

## Execution Phases

### Phase 1: Data Models (Base)
**Task:** 1
**Depends on:** Nothing
**Can run in parallel:** No (base for everything)

### Phase 2: Services + UI (Parallel)
**Tasks:** 2, 3, 6
**Depends on:** Phase 1
**Can run in parallel:** Yes (independent components)

### Phase 3: Integration (Parallel)
**Tasks:** 4, 5
**Depends on:** Phase 2
**Can run in parallel:** Yes (different files)

### Phase 4: Connection (Parallel)
**Tasks:** 7, 8
**Depends on:** Phase 3
**Can run in parallel:** Yes (different concerns)

### Phase 5: Final Test
**Task:** 9
**Depends on:** All previous phases

---

## Subagent Dispatch Plan

### Dispatch 1: Phase 1
- **Task:** 1 (Data Models)
- **Files:** LauncherManifest.cs, DetectedShader.cs, manifest.json
- **Output:** Updated models ready for services

### Dispatch 2: Phase 2 (Parallel)
- **Task 2:** ShaderDetectionService.cs
- **Task 3:** ShaderInstallerService.cs
- **Task 6:** MainWindow UI
- **Note:** These can run simultaneously as they're independent

### Dispatch 3: Phase 3 (Parallel)
- **Task 4:** GameInstallerService.cs integration
- **Task 5:** App.xaml.cs DI setup
- **Note:** Different files, no conflicts

### Dispatch 4: Phase 4 (Parallel)
- **Task 7:** Connect UI to services
- **Task 8:** Startup trigger
- **Note:** Both modify MainWindow.xaml.cs but different sections

### Dispatch 5: Phase 5
- **Task 9:** Final integration test
- **Note:** Manual testing required

---

## Conflict Resolution

**Potential conflicts:**
1. Tasks 7 and 8 both modify MainWindow.xaml.cs
   - **Solution:** Task 7 adds fields and methods, Task 8 adds startup logic
   - **Order:** Execute Task 7 first, then Task 8

2. Tasks 4 and 5 both affect DI
   - **Solution:** Task 4 modifies GameInstallerService, Task 5 modifies App.xaml.cs
   - **No conflict:** Different files

---

## Progress Tracking

| Phase | Tasks | Status |
|-------|-------|--------|
| 1 | Task 1 | ⏳ Pending |
| 2 | Tasks 2, 3, 6 | ⏳ Pending |
| 3 | Tasks 4, 5 | ⏳ Pending |
| 4 | Tasks 7, 8 | ⏳ Pending |
| 5 | Task 9 | ⏳ Pending |

---

## Estimated Time

- Phase 1: ~2 minutes
- Phase 2: ~5 minutes (parallel)
- Phase 3: ~3 minutes (parallel)
- Phase 4: ~3 minutes (parallel)
- Phase 5: ~5 minutes (manual test)

**Total:** ~18 minutes (vs ~30 minutes sequential)
