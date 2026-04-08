# Second Dawn RP — Fabric 1.21.1

A Star Trek-themed roleplay server mod for Fabric 1.21.1. Built for the Second Dawn RP server.

**Mod ID:** `seconddawnrp` | **Package:** `net.shard.seconddawnrp` | **Loom:** 1.15.5 | **Java:** 21

---

## Completed Phases

### Phase 1–3 — Core Profile & Task System
- Player profiles with division, rank, progression path, billets, certifications, duty status
- LuckPerms sync — rank and division pushed to permission groups on change
- Task system — active tasks, completion, officer approval, reward points
- Task terminals — physical blocks that filter available tasks by division
- SQLite persistence via JDBC, JSON backup layer
- `/profile` — unified command showing all player + character data

### Phase 4 — Engineering Degradation
- Component registration via tool — any block can be a tracked component
- Four-state health: NOMINAL → DEGRADED → CRITICAL → OFFLINE
- Automatic repair task generation when components degrade
- Engineering PAD item — code-drawn GUI showing all components, health bars, warp core status
- `/engineering` commands — register, locate, repair, set health

### Phase 4.5 — GM Tools
- **Environmental Effect Block** — radius-based vanilla status effect + medical condition application, configurable fire mode (continuous/on-entry) and linger mode
- **Trigger Block** — WALK_OVER and INTERACT modes, fires GM events or chat messages
- **Anomaly Marker** — visual markers for ongoing GM events
- **Tool Visibility Service** — GM items auto-show/hide based on held item
- **GM Registry** — persistent named location registry

### Phase 5 — Warp Core
- Multi-core support — multiple warp cores registered per server
- Fuel rods, resonance coils, containment cells as physical items
- State machine: OFFLINE → STARTING → ONLINE → UNSTABLE → CRITICAL → FAILED
- Power output pushed to TREnergy network each tick
- Physical controller block with GUI, warp core monitor screen
- `/warpcore` commands — register, fuel, start, stop, status

### Phase 5.25 — Terminal Designator + ComputerCraft Integration
- **Terminal Designator Tool** — designates any placed block in the world as a typed terminal
- **Terminal types:** OPS_TERMINAL, ENGINEERING_CONSOLE, ROSTER_CONSOLE (active) + MEDICAL, SECURITY, SCIENCE, TACTICAL, MISSION, RESOURCE, LIBRARY (stubbed)
- **Colored block outlines** — server-side DustParticleEffect wireframe per type
- **Action bar prompt** — name + "Right-click to open" shown above XP bar for all players
- **Persistent JSON registry** — `config/assets/seconddawnrp/terminal_designations.json`
- **ComputerCraft integration** — optional, zero impact if CC absent. `WarpCorePeripheral`, `DegradationPeripheral`, `OpsPeripheral` via `@LuaFunction`

### Phase 5.5 — Character System
- **CharacterProfile merged into PlayerProfile**
- Character fields: `characterName`, `species`, `bio`, `characterStatus`, `knownLanguages`, `universalTranslator`, `permadeathConsent`, `activeLongTermInjuryId`, `deceasedAt`, `progressionTransfer`, `serviceRecord`
- **SpeciesRegistry** — JSON-driven at `data/seconddawnrp/species/`, ships with `human.json`
- **CharacterCreationTerminalBlock** — physical block, 3-tab GUI (Identity → Bio → Confirm)
- **LongTermInjuryService** — three tiers (MINOR/MODERATE/SEVERE), tick refresh, treatment cooldown, Medical treatment API
- **RdmDetectionService** — automatic RDM flag generation, GM notification
- **CharacterArchiveRepository** — write-only death snapshots
- `/profile` unified command, `/gm character` override commands

### Phase 5.5 — Career Path Infrastructure
- Cadet track (CADET_1–4), division declaration gate, officer-approved promotion
- Graduation flow — two-step: instructor proposes → Captain approves
- Officer slot caps — configurable per-rank maximums with queue
- Officer progression points — automatic point awards for admin actions
- Commendation system — variable-point manual awards (Commander+)
- Ship positions — FIRST_OFFICER and SECOND_OFFICER designations
- Mustang flag — enlisted-to-commissioned notation
- Group tasks — capacity > 1, shared progress, distributed rewards

### Phase 5.5 — Roster GUI
- **Roster PAD item** — right-click to open division roster screen
- Two-panel layout — scrollable member list + selected member detail panel
- Member list — online indicator, character name, rank, division badge, sorted online-first
- Detail panel — full character data, rank points, service record, certs/billets, mustang flag
- Authority-gated action buttons: Promote/Demote, Cadet actions, Transfer/Dismiss, Commend
- Inline input overlay for graduation rank and commend reason
- Live refresh — server pushes updated data after every action

### Phase 6 — Dice + RP PADD System

#### Dice Engine
- `/roll` — d20 with rank bonus, certification bonuses, demerit penalties
- `/rp [action]` — third-person narration, broadcasts to all players
- `/gm rolls public/private`, group rolls, DC system, scenario management

#### RP PADD Item
- Physical item — right-click opens GUI with recording status, live log, Start/Stop, Sign

#### Submission Box Block
- Accepts signed RP PADDs, saves to database, notifies online officers

#### Officer Review
- Fifth tab on Ops PADD showing pending submissions
- CONFIRM / DISPUTE with inline note, generates Archive PADD, awards officer progression points

### Phase 8 — Medical System
- **MedicalConditionRegistry** — loads condition templates from JSON at `data/seconddawnrp/medical_conditions/`
- `critical_trauma.json` baked in as string constant, written via `ensureSystemConditions()` on reload
- **MedicalService** — treatment steps with timing windows (min/max seconds), failure consequences
- **TricorderItem** — countdown display gated on `showTimer: true` in JSON; open to all players; `isMedicalOfficer` controls scan depth only; self-treatment blocked
- **MedicalPadScreen** — GUI for Medical division players
- **Downed state** — `DownedService` + `DownedHooks` with synchronous `ALLOW_DEATH` interception
- **GurneyService** — transports downed players via invisible armor stands at Y-1.6 offset
- **Force respawn** — hold Sneak for 3 seconds after 2-minute wait
- **Low population mode** — triggers when fewer than 5 players online OR no Medical player online; grants Short-Term Injury (STI) instead of full LTI (MODERATE tier, 30-minute auto-expiry, 1 session to clear)
- **LongTermInjury** extended with `sessionsRequired` field (default -1 = use tier default)

### Phase 9 — Transporter System
- **TransporterService** — manages pad registrations, controller block state, destination queue
- Transporter pads are decorative only; the controller block is the sole functional element
- `/transporter ready` opt-in for targeting
- Destinations: named ship locations, GM-activated colony dimensions, player-follow, custom XYZ
- Access requires Operations division + Transporter Operator certification
- `/beamup` — colony players send pickup requests to the controller queue
- Colony dimension reachability is GM-activated for MVP; `proximityCheck()` hook left for Phase 12

### Phase 9 (partial) — Dimension / Location System
- **LocationRegistry** — JSON-driven dimension definitions at `data/seconddawnrp/locations/`
- **LocationService** — reachability calculation from ship position and warp speed
- Orbital zone data pushed to tactical screens via `StellarNavUpdatePayload`

### Phase 10 — Tactical System

#### Encounter Engine
- **EncounterService** — lifecycle: create, addship, start, pause, resume, end
- **ShipState** — logical representation: position, heading, speed, hull, shields (4 facings), power subsystems, torpedoes, warp state
- **ShipClassDefinition** — JSON-driven at `data/seconddawnrp/ships/` (ships `heavy_cruiser.json`, `light_destroyer.json`)
- **TacticalService** — tick orchestrator (5s interval): Power → Penalties → Movement → Shields → Weapons → Fire → Warp → Hull check → Broadcast
- **PowerService** — warp core output → power budget, auto/manual distribution, 30s manual timeout
- **ShipMovementService** — heading/speed interpolation, position update, evasive maneuver
- **ShieldService** — suppression ticking, power-scaled regen, facing distribution; regen halved if `zone.shield_emit` destroyed
- **WeaponService** — phaser/torpedo resolution, hardpoint cooldowns, hit chance calculation; player-selectable target facing; bridge/aft zone penalty integration
- **WarpService** — warp engagement/drop, power threshold checks
- **HullDamageService** — hull threshold events (NOMINAL/DAMAGED/CRITICAL/FAILING/DESTROYED), shield vulnerability threshold (25%), zone damage routing

#### Damage Zone System
- **DamageZone** — per-zone HP (`hullMax / zoneCount`), model + real block lists, bounding box for player proximity (3-block padding all axes), lazy-cached
- **DamageModelMapper** — pure executor: replaces real ship blocks with Air/Fire/Black Concrete/Gray Concrete on destroy; restores to stone bricks on repair
- **DamageZoneToolItem** — right-click block registers; sneak + right-click removes; right-click air cycles MODEL/REAL mode; locator particles on register/remove
- **ZoneRepairListener** — Engineering player right-clicks damaged block with 4 Stone Bricks to repair
- Facing → zone mapping baked in:
  - FORE → `zone.bridge`, `zone.weapons_fore`, `zone.torpedo_bay`, `zone.sensors`
  - AFT → `zone.engines`, `zone.engineering`
  - PORT/STARBOARD → `zone.shield_emit`, `zone.weapons_aft`, `zone.life_support`
- Zone stat penalties applied every tick after power distribution:
  - `zone.engines` — max speed capped at 50%
  - `zone.weapons_fore` — weapons power reduced 30%
  - `zone.torpedo_bay` — torpedo count forced to 0
  - `zone.engineering` — power budget reduced 25%
  - `zone.sensors` — sensor power forced to 0
  - `zone.shield_emit` — regen rate halved (checked by ShieldService)
  - `zone.bridge` — hit chance penalty -15% (checked by WeaponService)
  - `zone.weapons_aft` — aft-arc hardpoints unavailable
  - `zone.life_support` — status effects on players inside zone bounding box
- Zone block registrations persisted to `damage_zone_model_blocks` / `damage_zone_real_blocks` tables, loaded at server start and merged into zone objects when ships join encounters

#### Tactical Console GUI
- **TacticalScreen** — four-panel code-drawn GUI: Navigation, Weapons, Shields, Status
- Navigation panel — tactical map with encounter ships, shield rings, anomaly overlays; standby mode shows stellar nav map with orbital zones, ETA calculation, zoom (0.25×–4×), expand overlay
- Weapons panel — target list, FORE/AFT/PORT/STBD facing selector (AUTO when none selected), fire/torpedo/evasive buttons
- Shields panel — four facing bars with power-scaled display, balance button
- Status panel — hull bar, power breakdown, scrollable encounter log (mouse wheel + ▲/▼ buttons)
- **TacticalScreenHandler** — station filter (HELM/WEAPONS/SHIELDS/SENSORS/FULL), permission gating per station
- **GmShipConsoleScreen** — GM-only screen: ship selector, direct helm/speed input, target selection, fire buttons, shield bars, log

#### Networking
- `EncounterUpdatePayload` S→C — full state delta every combat tick
- `StandbyUpdatePayload` S→C — anomaly map pushed every 10s
- `OpenTacticalPayload` S→C — screen open with full state
- `StellarNavUpdatePayload` S→C — passive ship nav data (position, heading, speed, warp, orbital zones)
- `LocateZoneBlockS2CPacket` S→C — locator particles for zone block registration
- `WeaponFirePayload` C→S — includes `targetFacing` ("FORE"/"AFT"/"PORT"/"STARBOARD"/"AUTO")
- `HelmInputPayload`, `PowerReroutePayload`, `ShieldDistributePayload` C→S

#### Passive Ship Movement
- **PassiveShipMovementService** — home ship position/heading/speed persisted to `ship_position` table, updated on passive tick (every 30s)
- Stellar nav broadcast pushes home ship state to all open tactical screens

#### GM Commands
- `/gm encounter create|addship|start|pause|resume|end|list`
- `/gm ship status|navstatus|zones|zonedamage|zonerepair|power|heading|speed|torpedoes|warp|position|homenavstatus`
- `/admin ship register|unregister|list|sethomeship`
- `/admin hardpoint register|list|zone set|zone clear|zone remove|zone locate`
- `/admin shipyard set`

---

## Architecture

```
UI / Commands / Events
        ↓
    Services
        ↓
   Repositories
        ↓
  Storage (SQLite + JSON)
```

### Key Singletons (`SecondDawnRP.java`)
| Singleton | Purpose |
|---|---|
| `DATABASE_MANAGER` | SQLite connection pool |
| `PROFILE_MANAGER` | In-memory profile cache + dirty tracking |
| `PROFILE_SERVICE` | All player + character operations |
| `PERMISSION_SERVICE` | LuckPerms wrapper |
| `TASK_SERVICE` | Task assignment, completion, rewards |
| `TASK_REWARD_SERVICE` | Points calculation |
| `TASK_PERMISSION_SERVICE` | Task access control |
| `TERMINAL_MANAGER` | Physical terminal block registry |
| `GM_EVENT_SERVICE` | Encounter templates, spawn blocks |
| `GM_PERMISSION_SERVICE` | GM access control |
| `DEGRADATION_SERVICE` | Component health, repair tasks |
| `WARP_CORE_SERVICE` | Warp core state machine + energy output |
| `CHARACTER_ARCHIVE` | Write-only death snapshot repository |
| `LONG_TERM_INJURY_SERVICE` | LTI application, tick refresh, treatment |
| `RDM_DETECTION_SERVICE` | RDM flag generation + GM notification |
| `SPECIES_REGISTRY` | JSON species definitions |
| `ROLL_SERVICE` | Dice engine, DC, scenarios, group rolls |
| `RP_PADD_SERVICE` | Active recording session tracking |
| `RP_PADD_SUBMISSION_SERVICE` | Submission save, review, archive |
| `RP_PADD_ITEM` | Cast reference for archive PADD generation |
| `ENV_EFFECT_SERVICE` | Environmental effect blocks |
| `TRIGGER_SERVICE` | Trigger blocks |
| `ANOMALY_SERVICE` | Anomaly markers |
| `GM_TOOL_VISIBILITY_SERVICE` | GM item auto-show/hide |
| `GM_REGISTRY_SERVICE` | Named location registry |
| `TERMINAL_DESIGNATOR_REGISTRY` | Designated terminal block positions + types |
| `TERMINAL_DESIGNATOR_SERVICE` | Terminal interact dispatch + glow + action bar |
| `CADET_SERVICE` | Cadet rank track, graduation flow |
| `OFFICER_SLOT_SERVICE` | Slot caps, queue management, promotion notification |
| `OFFICER_PROGRESSION_SERVICE` | Automatic point awards for officer admin actions |
| `COMMENDATION_SERVICE` | Manual commendation issuance |
| `SHIP_POSITION_SERVICE` | First/Second Officer designation |
| `GROUP_TASK_SERVICE` | Group task sessions, shared progress, reward distribution |
| `ROSTER_SERVICE` | Roster data building, all roster actions |
| `MEDICAL_CONDITION_REGISTRY` | JSON condition templates (single shared instance) |
| `MEDICAL_SERVICE` | Treatment steps, timing windows, failure consequences |
| `MEDICAL_TERMINAL_SERVICE` | Medical terminal block state |
| `DOWNED_SERVICE` | Downed state tracking, force respawn |
| `GURNEY_SERVICE` | Armor stand transport for downed players |
| `LOCATION_REGISTRY` | JSON dimension definitions |
| `LOCATION_SERVICE` | Reachability calculation |
| `TRANSPORTER_SERVICE` | Pad registrations, controller state, destination queue |
| `ENCOUNTER_SERVICE` | Encounter lifecycle, ship registry, hardpoints |
| `TACTICAL_SERVICE` | Encounter tick orchestrator, zone management |
| `PASSIVE_SHIP_MOVEMENT_SERVICE` | Home ship passive nav, position persistence |

### Database Schema
| Table | Purpose |
|---|---|
| `players` | All player + character data (V1 + V5 + V7) |
| `player_billets` | Player billet assignments |
| `player_certifications` | Player certifications |
| `player_known_languages` | Live character languages (V5) |
| `player_active_tasks` | In-progress task state |
| `player_completed_tasks` | Task completion history |
| `ops_task_pool` | Officer-created task pool |
| `task_terminals` | Terminal block registrations |
| `components` | Degradation component registry |
| `character_profiles` | Deceased character archive (write-only) |
| `character_known_languages` | Languages at time of death (archive) |
| `long_term_injuries` | Active and historical LTIs (V10 adds `sessions_required`) |
| `rdm_flags` | RDM detection flags |
| `rp_padd_submissions` | RP PADD review queue (V6) |
| `officer_slot_queue` | Players queued for promotion when rank is full (V7) |
| `ship_registry` | Named vessel registry (V13) |
| `hardpoint_registry` | Weapon mount registrations (V13) |
| `damage_zone_registry` | Zone metadata per ship (V13) |
| `damage_zone_model_blocks` | Model block positions per zone (V13) |
| `damage_zone_real_blocks` | Real ship block positions per zone (V13) |
| `encounter_state` | Active encounter persistence (V13) |
| `encounter_ships` | Ships in active encounters (V13) |
| `shipyard_config` | Shipyard spawn point (V13) |
| `ship_position` | Home ship passive nav state (V14) |
| `schema_version` | Migration tracking |

### Critical Notes
1. **1.21.1 NBT API** — use `DataComponentTypes.CUSTOM_DATA` + `NbtComponent.of()`. `getOrCreateNbt()`, `setNbt()`, `getNbt()` are removed.
2. **BlockWithEntity subclasses** — must implement `getCodec()` returning `MapCodec`. Use `MapCodec.unit(ClassName::new)`.
3. **Command registration** — always in `onInitialize()` via `CommandRegistrationCallback`. Never inside `SERVER_STARTED`.
4. **LuckPerms block** — call `PROFILE_SERVICE.setProfileSyncService(syncService)`. Do NOT replace `PROFILE_SERVICE` instance.
5. **Payload registration** — must call `registerPayloads()` before `registerServerReceivers()` or crash on startup.
6. **TREnergy** — push-based. Use `Transaction.openOuter()` + `target.insert()` each tick, always commit.
7. **ProfileLtiCallback** — LongTermInjuryService uses this interface instead of CharacterRepository to update `activeLongTermInjuryId` on PlayerProfile.
8. **Extended screens** — screens that carry data to the client must use `ExtendedScreenHandlerFactory`. See `RosterScreenHandlerFactory`.
9. **CC integration** — all CC classes isolated in `.cc` package. `CCPeripheralRegistry` checks `FabricLoader.isModLoaded("computercraft")` before touching any CC API.
10. **Tick loop placement** — per-player calls like `tickActionBarPrompt` must be INSIDE the `for (var player : ...)` loop.
11. **Java 21** — required. Build target is `release = 21`.
12. **MEDICAL_CONDITION_REGISTRY** — must be instantiated exactly once in `SecondDawnRP.java` before `LongTermInjuryService` construction. Never duplicated.
13. **TacticalService constructor** — requires both `EncounterService` and `TacticalRepository`. `HullDamageService` gets the repository reference through `TacticalService`.
14. **Zone block persistence** — `TACTICAL_SERVICE.loadFromDatabase()` must be called in `SERVER_STARTED` after `ENCOUNTER_SERVICE.loadFromDatabase()`. Block registrations are merged into `DamageZone` objects when ships join encounters.
15. **Singleton discipline** — `zonesByShip` in `HullDamageService` is runtime-only (encounter-scoped). `damage_zone_model_blocks` / `damage_zone_real_blocks` are world-persistent. These are separate concerns.

---

## Config Files (auto-created on first boot)
- `config/assets/seconddawnrp/roll_modifiers.json`
- `config/assets/seconddawnrp/degradation_config.json`
- `config/assets/seconddawnrp/warp_core_config.json`
- `config/assets/seconddawnrp/cadet_config.json`
- `config/assets/seconddawnrp/officer_slots.json`
- `config/assets/seconddawnrp/officer_progression.json`
- `config/assets/seconddawnrp/terminal_designations.json` — delete on world wipe
- `config/assets/seconddawnrp/cc_programs/` — example Lua programs for CC monitors
- `data/seconddawnrp/species/*.json` — ships with `human.json`
- `data/seconddawnrp/ships/*.json` — ships with `heavy_cruiser.json`, `light_destroyer.json`
- `data/seconddawnrp/medical_conditions/*.json` — `critical_trauma.json` auto-written

## Resource Files Required
- `assets/seconddawnrp/textures/block/submission_box.png`
- `assets/seconddawnrp/textures/block/character_creation_terminal.png`
- `assets/seconddawnrp/blockstates/submission_box.json`
- `assets/seconddawnrp/blockstates/character_creation_terminal.json`
- `assets/seconddawnrp/models/block/submission_box.json`
- `assets/seconddawnrp/models/block/character_creation_terminal.json`
- `assets/seconddawnrp/models/item/submission_box.json`
- `assets/seconddawnrp/models/item/character_creation_terminal.json`
- `assets/seconddawnrp/models/item/rp_padd.json`
- `assets/seconddawnrp/models/item/roster_pad.json`
- `assets/seconddawnrp/models/item/terminal_designator_tool.json`
- `assets/seconddawnrp/textures/gui/operations_pad.png`

---

## Tech Stack
- Fabric 1.21.1
- TREnergy 4.1.0 (bundled)
- Tech Reborn + Energized Power (optional runtime)
- LuckPerms (optional — graceful fallback)
- CC:Tweaked 1.116.0+ (optional — compile-only, graceful fallback)
- SQLite via JDBC
- Loom 1.15.5 / Java 21