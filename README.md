# Second Dawn RP — Fabric Mod
**Minecraft 1.21.1 | Fabric Loader 0.18.4 | Mod ID: `seconddawnrp`**

A persistent sci-fi roleplay server mod built for a Star Trek-style ship RP server. Provides player profiles, task systems, GM tools, engineering degradation, and a warp core power system.
 
---

## Dependencies

| Mod | Version | Role |
|-----|---------|------|
| Fabric API | 0.116.9+1.21.1 | Required |
| Tech Reborn | 5.11.19 | Optional — energy network |
| Energized Power | 1.21.1-2.15.12 | Optional — energy network |
| LuckPerms | Latest | Optional — division/permission sync |
| TREnergy API | 4.1.0 | Bundled via `include modApi` |

**build.gradle:**
```groovy
include modApi("teamreborn:energy:4.1.0")
```
```groovy
maven { name = "TechReborn"; url = "https://maven.terraformersmc.com/" }
```
 
---

## Architecture Overview

```
SecondDawnRP.java          ← Main entrypoint, all singleton services
SecondDawnRPClient.java    ← Client initializer, packet receivers, screen openers
 
registry/
  ModBlocks.java           ← All block + block entity type registrations
  ModItems.java            ← All item registrations
 
profiles/                  ← Player profiles, divisions, LuckPerms sync
tasksystem/                ← Task creation, state, rewards, terminals
degradation/               ← Component registration, health, repair, Engineering Pad
gmevent/                   ← GM tools: env effects, triggers, anomaly markers, tool visibility
warpcore/                  ← Reactor state machine, energy output, monitor screen
character/                 ← Character service stub (Phase 6+)
```
 
---

## Completed Phases

### Phase 1–3 — Core Systems ✅
- Player profiles with division assignment (Command, Engineering, Medical, Security, Science)
- LuckPerms group sync — division set by LP group, synced on join
- Task system — pool tasks, personal tasks, manual confirm, auto-confirm objectives
- Task terminals — physical blocks players interact with to claim/complete tasks
- Operations pad item — GM tool for task management
- SQLite + JSON dual persistence

### Phase 4 — Engineering Degradation ✅
- **Component Registration Tool** — sneak+right-click any block to register as a ship component
- Components degrade over time: NOMINAL → DEGRADED → CRITICAL → OFFLINE
- Repair system — sneak+right-click with correct item consumes it and restores health
- Auto-generated Ops tasks when component hits CRITICAL
- OFFLINE blocks suppress interactions with a warning message
- **Engineering Pad** — right-click in air for full component overview, right-click warp core controller for focused view
- Hover tooltip: name, status, health, X/Y/Z coordinates, repair item + count, `/engineering locate` hint
- `/engineering locate <id>` — sends END_ROD beacon particles to specific player regardless of distance
- `/engineering register/remove/status/list/sethealth/setrepair/save`

### Phase 4.5 — GM Tools ✅
- **Environmental Effect Tool** — register blocks that apply potion effects + medical conditions in radius. Scrollable dropdown GUI.
- **Trigger Tool** — register trigger blocks. Modes: INTERACT, RADIUS. Actions: BROADCAST, ACTIVATE_LINKED, DEACTIVATE_LINKED, GENERATE_TASK, NOTIFY_GM, PLAY_SOUND. Full config GUI.
- **Anomaly Marker Tool** — register anomaly contacts. Types: ENERGY, BIOLOGICAL, GRAVITATIONAL, UNKNOWN. Config GUI. Activating notifies relevant divisions.
- **Tool Visibility** — holding a GM tool shows particle columns above registered blocks within 48 blocks. Distinct particles per tool: FLAME (components), SOUL_FIRE_FLAME (env), WITCH (triggers), END_ROD (anomalies), combo (warp core).
- `/gm env`, `/gm trigger`, `/gm anomaly` command suites

### Phase 5 — Warp Core ✅
- **Warp Core Controller Block** — single block, has block entity for energy output
- Registration via sneak+right-click with Warp Core Tool. Multiple cores supported, no limit.
- State machine: OFFLINE → STARTING → ONLINE → UNSTABLE → CRITICAL → FAILED
- **Startup assist** — requires 1000 E on an adjacent face to complete startup
- **Fuel rods** — right-click controller with fuel rods in main or off-hand
- **Energy output** — 2048 E/tick pushed to adjacent TR/EP cables. Scales with power% (UNSTABLE=60%, CRITICAL=25%)
- **TREnergy integration** — push-based via `Transaction.openOuter()` + `target.insert()` each tick
- **Resonance coils** — optional degradation components linked to reactor. Multiple coils, weighted health formula: `(average × 0.7) + (worst × 0.3)`. Low health → UNSTABLE. Dead → blocks startup.
- CRITICAL/FAILED doubles degradation drain rate on all components globally
- **Monitor screen** — state, power%, fuel bar, stability, coil health ×count. Physical Startup/Shutdown/Reset/Close buttons.
- `/warpcore list/status/startup/shutdown/reset/fuel/linkcoil/unlinkcoil/unlinkallcoils/sources/fault/unregister`
- Tab completion on component IDs for coil linking

---

## Items Reference

| Item ID | Usage |
|---------|-------|
| `seconddawnrp:engineering_pad` | Right-click air: component overview. Right-click controller: focused warp core view. |
| `seconddawnrp:component_registration_tool` | Sneak+right-click to register/unregister components |
| `seconddawnrp:environmental_effect_tool` | Register environmental hazard blocks |
| `seconddawnrp:trigger_tool` | Register trigger blocks |
| `seconddawnrp:anomaly_marker_tool` | Register anomaly contacts |
| `seconddawnrp:warp_core_tool` | Register/monitor warp cores. Sneak+right-click to register. |
| `seconddawnrp:fuel_rod` | Right-click warp core controller to load |
| `seconddawnrp:task_pad` | Player task management |
| `seconddawnrp:operations_pad` | GM task management |
 
---

## Blocks Reference

| Block ID | Notes |
|----------|-------|
| `seconddawnrp:warp_core_controller` | Has block entity. Exposes EnergyStorage.SIDED. |
| `seconddawnrp:warp_core_casing` | Decorative |
| `seconddawnrp:warp_core_injector` | Decorative |
| `seconddawnrp:warp_core_column` | Decorative, light level 8 |
| `seconddawnrp:conduit` | Decorative |
| `seconddawnrp:power_relay` | Decorative |
| `seconddawnrp:fuel_tank` | Decorative |
 
---

## Config Files
All configs live at `config/assets/seconddawnrp/` — **outside the world folder**.
**Delete `warpcore.json` and `components.json` when wiping worlds.**

| File | Contains |
|------|----------|
| `warpcore.json` | Registered cores (positions, state, fuel) |
| `warpcore_config.json` | Fuel drain, output rate, thresholds |
| `degradation_config.json` | Component drain rates, repair defaults |
| `components.json` | Registered component entries |
| `anomaly.json` | Registered anomaly markers |
| `triggers.json` | Registered trigger blocks |
| `env_effects.json` | Registered environmental effect blocks |
| `vanilla_effects_registry.json` | Available potion effects for env tool |
| `medical_conditions_registry.json` | Available medical conditions |
 
---

## Key Architectural Notes

**TREnergy push-based.** The warp core calls `target.insert()` each tick inside `Transaction.openOuter()`. Cables do not pull. Always commit the transaction or nothing transfers.

**Adjacent cable detection.** Query `EnergyStorage.SIDED.find(world, pos.offset(dir), dir.getOpposite())` — the adjacent block's face pointing back at the controller. Querying the controller's own faces returns null.

**Config vs world data.** All service data persists to `config/assets/seconddawnrp/` not the world save. Manual cleanup needed on world wipes.

**Block entity type registration.** Must use explicit lambda in `ModBlocks.register()`:
```java
/*BlockEntityType.Builder.create(
    (pos, state) -> new WarpCoreControllerBlockEntity(WARP_CORE_CONTROLLER_ENTITY, pos, state),
    WARP_CORE_CONTROLLER).build()*/
```
`WarpCoreControllerBlockEntity::new` causes a null TYPE at static init.

**Payload registration order.** `PayloadTypeRegistry` calls must come before `ServerPlayNetworking.registerGlobalReceiver` calls or the game crashes on startup.

**ComponentInteractListener priority.** The degradation listener fires via `UseBlockCallback` before `Block.onUse()`. If a block is both a registered component AND a registered warp core, the listener checks for warp core registration and returns `PASS` to let the block handle it.
 
---