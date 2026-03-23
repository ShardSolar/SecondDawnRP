# Second Dawn RP

A custom Fabric mod for Minecraft 1.21.1 implementing a persistent science fiction roleplay operating system. Players crew a single ship or station, organized into divisions with ranks, driven by a task-based gameplay loop, live GM events, an engineering maintenance system, and a warp core reactor.

---

## Asset Requests for Phase 5 (Warp Core)

The following items and blocks need textures or custom models before Phase 5 can be fully deployed. Pass this list to your modeller.

### Items (16×16 PNG textures)

| Item ID | Description | Notes |
|---|---|---|
| `engineering_pad` | Engineering division PADD | Already exists — amber/orange theme |
| `component_registration_tool` | GM tool for registering components | Screwdriver or tricorder style |
| `warp_core_tool` | GM tool for warp core configuration | Phase 5 |
| `fuel_rod` | Matter/antimatter fuel rod item | Glowing green or blue rod |
| `antimatter_pod` | Antimatter containment pod | Small canister, warning markings |
| `dilithium_crystal` | Dilithium crystal item | Faceted crystal, purple/white |

### Blocks (full block models + textures unless noted)

| Block ID | Description | Notes |
|---|---|---|
| `warp_core_casing` | Standard casing block for the M/ARA multiblock | Dark metal, amber trim |
| `warp_core_injector` | Injector block — top/bottom of the multiblock | Has a nozzle/port face |
| `warp_core_column` | Central glowing column block | Animated texture — cycles blue→white at full power, red at fault |
| `warp_core_controller` | Control interface block — the "brain" of the multiblock | Screen face with readouts |
| `plasma_conduit` | Horizontal/vertical conduit block | Connected texture preferred, pipe-style |
| ` eps_relay` | EPS relay junction block | Small panel-mounted block |
| `deuterium_tank` | Deuterium storage tank block | Cylindrical appearance, blue tint |

### GUI Textures (512×256 PNG atlas unless noted)

| File | Description | Notes |
|---|---|---|
| `warp_core_monitor.png` | Warp core status screen — reactor state, power output, fuel levels | Amber/orange theme matching engineering PAD |
| `fuel_management.png` | Fuel rod insertion/removal interface | |

---

## Overview

Second Dawn RP is a roleplay engine — a suite of interconnected systems that turn a Minecraft server into a persistent sci-fi crew simulation. The mod handles player profiles, division assignments, rank progression, task creation and tracking, in-world terminals, GM event tools with full skill systems, engineering maintenance, and a warp core reactor system — all wired together through a strict layered architecture.

**Platform:** Minecraft Java 1.21.1 — Fabric
**Mod ID:** `seconddawnrp`
**Setting:** Original IP — Star Trek inspired universe
**Crew capacity:** 300 players aboard a single vessel

---

## Architecture

All systems follow a strict layered contract. No layer may skip a level.

```
UI / Commands / Events
        ↓
    Services
        ↓
  Repositories
        ↓
Storage (JSON or SQLite)
```

### Core Singletons (`SecondDawnRP.java`)

| Field | Type | Purpose |
|---|---|---|
| `PROFILE_MANAGER` | `PlayerProfileManager` | Load, cache, save player profiles |
| `PROFILE_SERVICE` | `PlayerProfileService` | Profile lifecycle + LuckPerms sync |
| `TASK_SERVICE` | `TaskService` | Full task lifecycle |
| `TASK_REWARD_SERVICE` | `TaskRewardService` | Grant rank points on completion |
| `TASK_PERMISSION_SERVICE` | `TaskPermissionService` | Permission checks for task operations |
| `TERMINAL_MANAGER` | `TaskTerminalManager` | Terminal registration and interaction |
| `DATABASE_MANAGER` | `DatabaseManager` | SQLite connection (auto-reconnect) |
| `PERMISSION_SERVICE` | `PermissionService` | LuckPerms wrapper |
| `GM_EVENT_SERVICE` | `GmEventService` | GM event lifecycle, skill ticks, mob tracking |
| `GM_PERMISSION_SERVICE` | `GmPermissionService` | Permission checks for GM operations |
| `DEGRADATION_SERVICE` | `DegradationService` | Component health, drain, repair, integrity checks |

---

## Divisions

| Division | Identity | Primary Loop |
|---|---|---|
| Command | Leadership, diplomacy | Direct crew, approve tasks, manage events |
| Operations | Logistics, coordination | Create/assign tasks, manage pool |
| Engineering | Ship systems, maintenance | Monitor reactor, repair components |
| Medical | Crew survival | Scan, diagnose, treat, follow-up care |
| Science | Research, exploration | Investigate anomalies, run experiments |
| Security | Awareness, threat response | Monitor cameras, scan, respond to threats |

---

## Task System

### Lifecycle
```
CREATE → ASSIGN → ACCEPT → IN_PROGRESS → AWAITING_REVIEW → COMPLETED
                                       ↘ FAILED / CANCELED / RETURNED
```

### Objective Types

| Type | Trigger | Target Format |
|---|---|---|
| `BREAK_BLOCK` | Block break event | `minecraft:block_id` |
| `COLLECT_ITEM` | Inventory delta (per tick) | `minecraft:item_id` |
| `VISIT_LOCATION` | Position check (every 20 ticks) | `x,y,z` or `x,y,z,radius` |
| `MANUAL_CONFIRM` | Player PADD submit button | Any descriptive string |

### Task ID Generation
Task IDs are auto-generated server-side from the display name — GMs do not set IDs manually. `Break Stone` → `break_stone`. Collisions auto-suffix: `break_stone_2`.

### Commands
```
/task assign <player> <taskId>
/task accept <taskId>
/task list [player]
/task progress [player]
/task approve <player> <taskId>
/task completed [player]
/task history [player]
/task debug advance <player> <id> <n>
```

### Task Terminals

World blocks registered by admin using the **Task Terminal Tool**. Right-click to open Terminal GUI.

| Type | Shows |
|---|---|
| `PUBLIC_BOARD` | Tasks with status `PUBLIC` |
| `DIVISION_BOARD` | `UNASSIGNED` tasks filtered by division |

**Terminal Tool Controls:**
- Right-click air — cycle terminal type
- Sneak + right-click air — cycle division filter
- Right-click block — register terminal
- Sneak + right-click block — remove terminal

---

## GM Event System

### Items

| Item | Purpose |
|---|---|
| `spawn_block_config_tool` | Full template editor, anchored to a world block |
| `spawn_item_tool` | Read-only quick-fire spawner |

### Spawn Block Config Tool Workflow
1. Right-click air to cycle and select a template
2. Right-click a block — registers it with the selected template, opens Config GUI
3. In GUI: edit mob, HP, armor, count, behaviour, effects/skills, linked task
4. Click **SAVE TEMPLATE** — saves edits permanently to JSON
5. Click **ACTIVATE SPAWN** — triggers event at that block position
6. Sneak + right-click block — removes registration

### Spawn Item Tool Workflow
1. Right-click — opens read-only GUI showing template list and selected template stats
2. Select a template from the list
3. Close GUI
4. Hold tool, aim at location, press **G** — spawns encounter at crosshair
5. Press **H** — despawns all active event mobs

### Keybindings
Configurable in Options → Controls → **Second Dawn RP — GM Tools**

| Key | Default | Action |
|---|---|---|
| GM Spawn | G | Spawns selected template at crosshair (Spawn Item Tool must be in main hand) |
| GM Despawn All | H | Despawns all active event mobs |

### Spawn Behaviours

| Behaviour | Effect |
|---|---|
| `INSTANT` | All mobs spawn at once within radius |
| `TIMED` | Mobs trickle in on interval until total reached |
| `ON_ACTIVATE` | Waits for manual GM activation |

### GM Commands
```
/gmevent templates
/gmevent spawn <templateId>
/gmevent spawnlinked <templateId> <taskId>
/gmevent list
/gmevent stop <eventId>
/gmevent stopall
```

### Encounter Templates (JSON)

Location: `run/config/assets/seconddawnrp/encounter_templates.json`

```json
[
  {
    "id": "boarding_party",
    "displayName": "Boarding Party",
    "mobTypeId": "minecraft:zombie",
    "maxHealth": 40.0,
    "armor": 10.0,
    "totalSpawnCount": 6,
    "maxActiveAtOnce": 3,
    "spawnRadiusBlocks": 8,
    "spawnIntervalTicks": 60,
    "spawnBehaviour": "TIMED",
    "statusEffects": ["skill:WEAKNESS_AOE", "minecraft:strength:1"],
    "heldItems": [],
    "drops": []
  }
]
```

### GM Skills

| Skill Key | Trigger | Effect |
|---|---|---|
| `skill:WEAKNESS_AOE` | Every 2s (passive) | Pulses Weakness I to players within 6 blocks |
| `skill:KNOCKBACK_STRIKE` | On hit | Extra knockback on every attack |
| `skill:REGENERATION` | Every 2s (passive) | Restores 1 HP per tick cycle |
| `skill:FIRE_AURA` | Every 1s (passive) | Sets players within 4 blocks on fire for 3s |
| `skill:ENRAGE` | Continuous check | Speed II + Strength II when below 50% HP |
| `skill:SHIELD_ALLIES` | Every 1s (passive) | Pulses Resistance I to nearby event mobs within 8 blocks |
| `skill:TELEPORT_BEHIND` | On hit | Teleports mob behind its attacker |
| `skill:SUMMON_ADDS` | On death | Spawns 2 silverfish at death location |

### Natural Death Prevention

Global config: `run/config/assets/seconddawnrp/gmevent_config.json`

```json
{
  "preventSunlightDamage": true,
  "preventNaturalDespawn": true,
  "preventSuffocation": false,
  "preventDrowning": false,
  "preventFallDamage": false
}
```

---

## Engineering Degradation System

### Overview

Components are registered in-world by GMs using the **Component Registration Tool**. Each component degrades over time and can be damaged by explosions and combat. Engineering players repair components using required items.

### Component States

| Status | Health Range | Drain Rate | Effect |
|---|---|---|---|
| NOMINAL | 76–100 | 1 HP/tick | None |
| DEGRADED | 51–75 | 2 HP/tick | Particle warnings pulse every 60s |
| CRITICAL | 26–50 | 3 HP/tick | Particles every 20s, repair task auto-generated |
| OFFLINE | 0–25 | 3 HP/tick | Heavy smoke, all actions locked |

### Component Registration Tool

| Interaction | Action |
|---|---|
| Sneak + right-click unregistered block | Begin registration (name → repair item) |
| Right-click registered block | Inspect status |
| Sneak + right-click registered block | Remove registration |

Registration is a two-step chat flow: type a display name, then a repair item ID with optional count (e.g. `minecraft:iron_ingot 2`) or `default` for the global default.

### Engineering PAD

| Interaction | Action |
|---|---|
| Right-click in air | Open component overview screen |
| Right-click registered block | Inspect status |
| Sneak + right-click registered block | Repair (consumes required items) |

### Repair System

Each component has a required repair item and count, configurable per component or falling back to the global default in `degradation_config.json`. The player must hold the correct item in their main hand. Items are consumed on repair.

### Engineering Commands

```
/engineering list
/engineering remove <id>
/engineering sethealth <id> <value>
/engineering setrepair <id> <item_id> [count]
/engineering clearrepair <id>
/engineering save
```

### Configuration

`config/assets/seconddawnrp/degradation_config.json`

```json
{
  "drainIntervalMs": 300000,
  "drainPerTickNominal": 1,
  "drainPerTickDegraded": 2,
  "drainPerTickCritical": 3,
  "taskGenerationCooldownMs": 1800000,
  "healthPerRepair": 20,
  "warningRadiusBlocks": 16,
  "warningPulseTicksDegraded": 1200,
  "warningPulseTicksCritical": 400,
  "defaultRepairItemId": "minecraft:iron_ingot",
  "defaultRepairItemCount": 1
}
```

### Explosion and Integrity Handling

The `ComponentIntegrityChecker` runs every 5 ticks. If a registered block is found to be air (destroyed by explosion, /setblock, or any external cause), the component takes 100 damage and is auto-unregistered. On server start, any components whose world no longer exists are purged automatically.

---

## Items

| Item | ID | Purpose |
|---|---|---|
| Task PADD | `task_pad` | Player mission display |
| Operations PADD | `operations_pad` | Officer task management |
| Engineering PADD | `engineering_pad` | Engineering component overview |
| Task Terminal Tool | `task_terminal_tool` | Admin terminal registration |
| Component Registration Tool | `component_registration_tool` | GM component registration |
| Spawn Block Config Tool | `spawn_block_config_tool` | GM encounter editor |
| Spawn Item Tool | `spawn_item_tool` | GM quick-fire spawner |

---

## Storage

| Data | Backend | Location |
|---|---|---|
| Player profiles | SQLite | `config/seconddawnrp/seconddawnrp.db` → `players` |
| Active tasks | SQLite | `player_active_tasks` table |
| Completed tasks | SQLite | `player_completed_tasks` table |
| Ops task pool | SQLite | `ops_task_pool` table |
| Task terminals | SQLite | `task_terminals` table |
| Components | JSON | `config/assets/seconddawnrp/components.json` |
| Task templates | JSON (read-only) | `data/seconddawnrp/tasks/` |
| Encounter templates | JSON | `config/assets/seconddawnrp/encounter_templates.json` |
| Spawn blocks | JSON | `config/assets/seconddawnrp/spawn_blocks.json` |
| GM event config | JSON | `config/assets/seconddawnrp/gmevent_config.json` |
| Degradation config | JSON | `config/assets/seconddawnrp/degradation_config.json` |

### Schema Version History

| Version | Changes |
|---|---|
| 1 | players, player_billets, player_certifications, player_active_tasks, player_completed_tasks |
| 2 | ops_task_pool, task_terminals |
| 3 | components |

---

## Permissions (LuckPerms Nodes)

| Node | Purpose |
|---|---|
| `st.task.create` | Create tasks via Ops PADD |
| `st.task.assign` | Assign tasks to players/divisions |
| `st.task.publish` | Publish tasks to public pool |
| `st.task.approve` | Approve manual confirm tasks |
| `st.task.review` | Return tasks for revision |
| `st.task.fail` | Fail tasks |
| `st.task.cancel` | Cancel tasks |
| `st.task.edit` | Edit existing pool tasks |
| `st.task.ops` | Open Ops PADD |
| `st.gm.use` | General GM tool access |
| `st.gm.spawnblock` | Configure spawn blocks |
| `st.gm.trigger` | Trigger events |
| `st.gm.stop` | Stop events |
| `st.gm.templates` | Save/manage encounter templates |
| `st.engineering.admin` | Register/remove components, engineering admin commands |

---

## Package Structure

```
net.shard.seconddawnrp
├── SecondDawnRP.java                  Mod initializer, static singletons
├── SecondDawnRPClient.java            Client screen + keybinding registration
├── database/
│   ├── DatabaseManager                SQLite connection (auto-reconnect)
│   ├── DatabaseConfig                 JDBC URL builder
│   ├── DatabaseBootstrap              Runs migrations on startup
│   └── DatabaseMigrations             Versioned schema migrations (v1, v2, v3)
├── divison/
│   └── Division                       Enum: COMMAND, OPERATIONS, ENGINEERING,
│                                            SCIENCE, MEDICAL, SECURITY
├── playerdata/
│   ├── PlayerProfile
│   ├── PlayerProfileManager
│   ├── PlayerProfileService
│   ├── LuckPermsSyncService
│   ├── PermissionService
│   └── persistence/
│       ├── ProfileRepository
│       └── SqlProfileRepository
├── tasksystem/
│   ├── command/    TaskCommands
│   ├── data/       ActiveTask, CompletedTaskRecord, OpsTaskPoolEntry,
│   │               TaskTemplate, TaskObjectiveType, OpsTaskStatus,
│   │               TaskAssignmentSource
│   ├── event/      TaskEventRegistrar, BlockBreakTaskListener,
│   │               CollectItemTaskListener, VisitLocationTaskListener
│   ├── loader/     TaskJsonLoader
│   ├── network/    ModNetworking, CreateTaskC2SPacket, AssignTaskC2SPacket,
│   │               ReviewTaskActionC2SPacket, AcceptTerminalTaskC2SPacket,
│   │               OpsPadRefreshS2CPacket, EditTaskC2SPacket,
│   │               SubmitManualConfirmC2SPacket
│   ├── pad/        TaskPadScreen, TaskPadScreenHandler, TaskPadItem,
│   │               OperationsPadScreen, AdminTaskScreenHandler, OperationsPadItem
│   ├── registry/   TaskRegistry
│   ├── repository/ TaskStateRepository, SqlTaskStateRepository,
│   │               JsonTaskStateRepository, OpsTaskPoolRepository,
│   │               SqlOpsTaskPoolRepository, JsonOpsTaskPoolRepository
│   ├── service/    TaskService, TaskRewardService, TaskPermissionService
│   ├── terminal/   TaskTerminalManager, TaskTerminalEntry, TaskTerminalToolItem,
│   │               TerminalType, TerminalScreen, TerminalScreenHandler,
│   │               TerminalScreenOpenData, TerminalScreenHandlerFactory,
│   │               TerminalInteractListener, TaskTerminalRepository,
│   │               SqlTerminalRepository, JsonTaskTerminalRepository
│   └── util/       TaskTargetMatcher
├── gmevent/
│   ├── client/     GmKeybindings, GmKeyInputHandler
│   ├── command/    GmEventCommands
│   ├── data/       EncounterTemplate, SpawnBlockEntry, ActiveEvent,
│   │               SpawnBehaviour, GmSkill, GmEventConfig
│   ├── event/      MobDeathEventListener, GmDamageListener, GmMobHitListener
│   ├── item/       SpawnBlockConfigTool, SpawnItemTool
│   ├── network/    SaveTemplateC2SPacket, ActivateSpawnBlockC2SPacket,
│   │               PushToPoolC2SPacket, FireSpawnC2SPacket,
│   │               DespawnAllC2SPacket, GmToolRefreshS2CPacket
│   ├── repository/ EncounterTemplateRepository, JsonEncounterTemplateRepository,
│   │               SpawnBlockRepository, JsonSpawnBlockRepository,
│   │               GmEventConfigRepository
│   ├── screen/     SpawnConfigScreen, SpawnConfigScreenHandler,
│   │               SpawnConfigScreenOpenData, SpawnConfigScreenHandlerFactory,
│   │               SpawnItemScreen, SpawnItemScreenHandler,
│   │               SpawnItemScreenOpenData, SpawnItemScreenHandlerFactory
│   └── service/    GmEventService, GmPermissionService, GmSkillHandler
├── degradation/
│   ├── client/     ComponentWarningClientHandler
│   ├── command/    EngineeringCommands
│   ├── data/       ComponentEntry, ComponentStatus, DegradationConfig
│   ├── event/      ComponentInteractListener, ComponentNamingChatListener,
│   │               ComponentDamageListener, ComponentBlockBreakListener
│   ├── item/       EngineeringPadItem, ComponentRegistrationTool
│   ├── network/    ComponentWarningS2CPacket, OpenEngineeringPadS2CPacket,
│   │               DegradationNetworking
│   ├── repository/ ComponentRepository, JsonComponentRepository,
│   │               SqlComponentRepository, DegradationConfigRepository
│   ├── screen/     EngineeringPadScreen
│   └── service/    DegradationService, ComponentIntegrityChecker
└── registry/
    ├── ModItems
    └── ModScreenHandlers
```

---

## Resource Files

```
src/main/resources/assets/seconddawnrp/
├── textures/gui/
│   ├── task_pad.png
│   ├── operations_pad.png
│   ├── engineering_pad.png
│   ├── terminal.png
│   ├── spawn_block_config_gui.png
│   └── spawn_item_gui.png
└── textures/item/
    ├── task_pad.png
    ├── operations_pad.png
    ├── engineering_pad.png
    ├── task_terminal_tool.png
    ├── component_registration_tool.png
    ├── spawn_block_config_tool.png
    └── spawn_item_tool.png

src/main/resources/assets/seconddawnrp/lang/
└── en_us.json

src/main/resources/data/seconddawnrp/
└── tasks/
```

---

## Development Roadmap

| Phase | Status | Description |
|---|---|---|
| 1 — Core gameplay | ✅ Complete | Profiles, divisions, full task system, PADDs, terminals, triggers, textures |
| 2 — SQL persistence | ✅ Complete | All repositories migrated to SQLite, JSON kept as backup |
| 3 — GM event system | ✅ Complete | Spawn blocks, spawn item, encounter templates, skills, keybindings, natural death prevention |
| 4 — Engineering degradation | ✅ Complete | Component registration tool, health drain, auto tasks, repair with item consumption, explosion handling, Engineering PAD |
| 5 — Warp core | 🔨 In Progress | M/ARA multiblock, reactor states, fuel system, fault events |
| 6 — Medical systems | 📋 Planned | Injury system, downed state, gurney, tricorder, treatment tools |
| 7 — Security systems | 📋 Planned | Camera network, sensor network, alert system, containment |
| 8 — Science & polish | 🔮 Future | Research terminals, anomaly system, rank progression, certifications |

---

## Dependencies

- **Fabric API** `0.116.9+1.21.1`
- **LuckPerms** `5.4.140` — optional, degrades to `NoOpProfileSyncService`
- **SQLite JDBC** — bundled
- **Lithium** — performance, compatible

---

## Known Limitations

- `CollectItemTaskListener` uses inventory snapshot delta (tick-based) — items consumed by crafting affect the count. Mixin-based approach planned for V2.
- Task approval requires assigned player to be online.
- Timed spawn mode uses overworld as fallback — stored event origin position is a planned V2 improvement.
- `onMobHit` skills (KNOCKBACK_STRIKE, TELEPORT_BEHIND) require `GmMobHitListener` to be registered.
- Component registration tool naming flow suppresses chat messages — players in naming mode cannot send public chat until they complete or cancel registration.

---

## Architecture Rules — Non-Negotiable

1. UI / Commands / Events must never call repositories directly
2. Services must never access Minecraft client APIs
3. Every new system gets a repository interface before any implementation
4. `PlayerProfileManager`, `TaskService`, `ProfileRepository`, `TaskStateRepository` must never be refactored without full impact analysis
