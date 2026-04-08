package net.shard.seconddawnrp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.character.*;
import net.shard.seconddawnrp.database.DatabaseBootstrap;
import net.shard.seconddawnrp.database.DatabaseConfig;
import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.database.DatabaseMigrations;
import net.shard.seconddawnrp.degradation.ComponentInteractionBlocker;
import net.shard.seconddawnrp.degradation.command.EngineeringCommands;
import net.shard.seconddawnrp.degradation.data.DegradationConfig;
import net.shard.seconddawnrp.degradation.event.ComponentBlockBreakListener;
import net.shard.seconddawnrp.degradation.event.ComponentDamageListener;
import net.shard.seconddawnrp.degradation.event.ComponentInteractListener;
import net.shard.seconddawnrp.degradation.event.ComponentNamingChatListener;
import net.shard.seconddawnrp.degradation.network.DegradationNetworking;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;
import net.shard.seconddawnrp.degradation.repository.DegradationConfigRepository;
import net.shard.seconddawnrp.degradation.repository.JsonComponentRepository;
import net.shard.seconddawnrp.degradation.service.DegradationService;
import net.shard.seconddawnrp.dice.command.GmRollCommands;
import net.shard.seconddawnrp.dice.command.RollCommands;
import net.shard.seconddawnrp.dice.data.RollModifierConfig;
import net.shard.seconddawnrp.dice.data.RpPaddSubmission;
import net.shard.seconddawnrp.dice.item.RpPaddItem;
import net.shard.seconddawnrp.dice.network.RpPaddNetworking;
import net.shard.seconddawnrp.dice.service.RollService;
import net.shard.seconddawnrp.dice.service.RpPaddService;
import net.shard.seconddawnrp.dice.service.RpPaddSubmissionService;
import net.shard.seconddawnrp.dimension.GmLocationCommands;
import net.shard.seconddawnrp.dimension.LocationRegistry;
import net.shard.seconddawnrp.dimension.LocationService;
import net.shard.seconddawnrp.gmevent.client.AnomalyClientHandler;
import net.shard.seconddawnrp.gmevent.command.GmAnomalyCommands;
import net.shard.seconddawnrp.gmevent.command.GmEnvCommands;
import net.shard.seconddawnrp.gmevent.command.GmEventCommands;
import net.shard.seconddawnrp.gmevent.command.GmTriggerCommands;
import net.shard.seconddawnrp.gmevent.data.GmEventConfig;
import net.shard.seconddawnrp.gmevent.data.TriggerMode;
import net.shard.seconddawnrp.gmevent.event.GmDamageListener;
import net.shard.seconddawnrp.gmevent.event.GmMobHitListener;
import net.shard.seconddawnrp.gmevent.event.MobDeathEventListener;
import net.shard.seconddawnrp.gmevent.network.GmEventNetworking;
import net.shard.seconddawnrp.gmevent.network.OpenAnomalyConfigS2CPacket;
import net.shard.seconddawnrp.gmevent.network.SaveAnomalyConfigC2SPacket;
import net.shard.seconddawnrp.gmevent.repository.*;
import net.shard.seconddawnrp.gmevent.service.*;
import net.shard.seconddawnrp.medical.*;
import net.shard.seconddawnrp.playerdata.DefaultProfileFactory;
import net.shard.seconddawnrp.playerdata.LuckPermsGroupMapper;
import net.shard.seconddawnrp.playerdata.LuckPermsPermissionService;
import net.shard.seconddawnrp.playerdata.LuckPermsSyncService;
import net.shard.seconddawnrp.playerdata.NoOpPermissionService;
import net.shard.seconddawnrp.playerdata.NoOpProfileSyncService;
import net.shard.seconddawnrp.playerdata.PermissionService;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileCommands;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.playerdata.PlayerProfileService;
import net.shard.seconddawnrp.playerdata.ProfileSyncService;
import net.shard.seconddawnrp.playerdata.persistence.ProfileRepository;
import net.shard.seconddawnrp.playerdata.persistence.SqlProfileRepository;
import net.shard.seconddawnrp.progression.*;
import net.shard.seconddawnrp.registry.ModBlocks;
import net.shard.seconddawnrp.registry.ModItems;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.roster.data.RosterOpenData;
import net.shard.seconddawnrp.roster.screen.RosterScreenHandler;
import net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking;
import net.shard.seconddawnrp.tasksystem.command.TaskCommands;
import net.shard.seconddawnrp.tasksystem.event.TaskEventRegistrar;
import net.shard.seconddawnrp.tasksystem.loader.TaskJsonLoader;
import net.shard.seconddawnrp.tasksystem.network.ModNetworking;
import net.shard.seconddawnrp.tasksystem.registry.TaskRegistry;
import net.shard.seconddawnrp.tasksystem.repository.JsonOpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.JsonTaskStateRepository;
import net.shard.seconddawnrp.tasksystem.repository.OpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.SqlOpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.SqlTaskStateRepository;
import net.shard.seconddawnrp.tasksystem.repository.TaskStateRepository;
import net.shard.seconddawnrp.tasksystem.service.TaskPermissionService;
import net.shard.seconddawnrp.tasksystem.service.TaskRewardService;
import net.shard.seconddawnrp.tasksystem.service.TaskService;
import net.shard.seconddawnrp.tasksystem.terminal.JsonTaskTerminalRepository;
import net.shard.seconddawnrp.tasksystem.terminal.SqlTerminalRepository;
import net.shard.seconddawnrp.tasksystem.terminal.TaskTerminalManager;
import net.shard.seconddawnrp.tasksystem.terminal.TaskTerminalRepository;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalInteractListener;
import net.shard.seconddawnrp.terminal.TerminalDesignatorInteractListener;
import net.shard.seconddawnrp.terminal.TerminalDesignatorRegistry;
import net.shard.seconddawnrp.terminal.TerminalDesignatorService;
import net.shard.seconddawnrp.transporter.AdminTransporterCommands;
import net.shard.seconddawnrp.transporter.TransporterCommands;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking;
import net.shard.seconddawnrp.transporter.TransporterService;
import net.shard.seconddawnrp.warpcore.command.WarpCoreCommands;
import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;
import net.shard.seconddawnrp.warpcore.network.WarpCoreNetworking;
import net.shard.seconddawnrp.warpcore.repository.JsonWarpCoreRepository;
import net.shard.seconddawnrp.warpcore.repository.WarpCoreConfigRepository;
import net.shard.seconddawnrp.warpcore.service.WarpCoreService;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class SecondDawnRP implements ModInitializer {

    public static final String MOD_ID = "seconddawnrp";

    // ── Core singletons ───────────────────────────────────────────────────────

    public static DatabaseManager DATABASE_MANAGER;
    public static PlayerProfileManager PROFILE_MANAGER;
    public static PlayerProfileService PROFILE_SERVICE;
    public static PermissionService PERMISSION_SERVICE;
    public static TaskRewardService TASK_REWARD_SERVICE;
    public static TaskService TASK_SERVICE;
    public static TaskPermissionService TASK_PERMISSION_SERVICE;
    public static TaskTerminalManager TERMINAL_MANAGER;
    public static GmEventService GM_EVENT_SERVICE;
    public static GmPermissionService GM_PERMISSION_SERVICE;
    public static DegradationService DEGRADATION_SERVICE;
    public static WarpCoreService WARP_CORE_SERVICE;
    public static CharacterArchiveRepository CHARACTER_ARCHIVE;
    public static LongTermInjuryService LONG_TERM_INJURY_SERVICE;
    public static RdmDetectionService RDM_DETECTION_SERVICE;
    public static SpeciesRegistry SPECIES_REGISTRY;
    public static RollService ROLL_SERVICE;
    public static RpPaddService RP_PADD_SERVICE;
    public static RpPaddSubmissionService RP_PADD_SUBMISSION_SERVICE;
    public static RpPaddItem RP_PADD_ITEM;
    public static EnvironmentalEffectService ENV_EFFECT_SERVICE;
    public static TriggerService TRIGGER_SERVICE;
    public static GmRegistryService GM_REGISTRY_SERVICE;
    public static AnomalyService ANOMALY_SERVICE;
    public static GmToolVisibilityService GM_TOOL_VISIBILITY_SERVICE;
    public static DownedService DOWNED_SERVICE;
    public static GurneyService GURNEY_SERVICE;




    // ── Phase 5.25 ────────────────────────────────────────────────────────────

    public static TerminalDesignatorRegistry TERMINAL_DESIGNATOR_REGISTRY;
    public static TerminalDesignatorService  TERMINAL_DESIGNATOR_SERVICE;
    public static net.shard.seconddawnrp.cc.DegradationPeripheral CC_DEGRADATION_PERIPHERAL;
    public static net.shard.seconddawnrp.cc.OpsPeripheral          CC_OPS_PERIPHERAL;

    // ── Phase 5.5 ─────────────────────────────────────────────────────────────

    public static CadetService              CADET_SERVICE;
    public static OfficerSlotService        OFFICER_SLOT_SERVICE;
    public static OfficerProgressionService OFFICER_PROGRESSION_SERVICE;
    public static CommendationService       COMMENDATION_SERVICE;
    public static ShipPositionService       SHIP_POSITION_SERVICE;
    public static GroupTaskService          GROUP_TASK_SERVICE;
    public static ServiceRecordService SERVICE_RECORD_SERVICE;
    public static net.shard.seconddawnrp.roster.service.RosterService ROSTER_SERVICE;

    // ── Phase 8 — Medical ─────────────────────────────────────────────────────

    public static MedicalConditionRegistry MEDICAL_CONDITION_REGISTRY;
    public static MedicalService           MEDICAL_SERVICE;
    public static MedicalTerminalService   MEDICAL_TERMINAL_SERVICE;
    public static LocationRegistry LOCATION_REGISTRY;
    public static LocationService LOCATION_SERVICE;
    public static TransporterService TRANSPORTER_SERVICE;
    public static net.shard.seconddawnrp.tactical.service.TacticalService TACTICAL_SERVICE;
    public static net.shard.seconddawnrp.tactical.service.EncounterService ENCOUNTER_SERVICE;
    public static net.shard.seconddawnrp.tactical.data.PassiveShipMovementService PASSIVE_SHIP_MOVEMENT_SERVICE;

    // ── onInitialize ──────────────────────────────────────────────────────────

    @Override
    public void onInitialize() {

        ModItems.register();
        ModBlocks.register();
        ModScreenHandlers.register();

        RP_PADD_ITEM = (RpPaddItem) ModItems.RP_PADD;

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ModItems.TASK_PAD);
            entries.add(ModItems.OPERATIONS_PAD);
            entries.add(ModItems.TASK_TERMINAL_TOOL);
            entries.add(ModItems.TERMINAL_DESIGNATOR_TOOL);
            entries.add(ModItems.ENGINEERING_PAD);
            entries.add(ModItems.COMPONENT_REGISTRATION_TOOL);
            entries.add(ModItems.WARP_CORE_TOOL);
            entries.add(ModItems.FUEL_ROD);
            entries.add(ModItems.CONTAINMENT_CELL);
            entries.add(ModItems.RESONANCE_COIL);
            entries.add(ModItems.ENVIRONMENTAL_EFFECT_TOOL);
            entries.add(ModItems.TRIGGER_TOOL);
            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_CASING));
            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_INJECTOR));
            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_COLUMN));
            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_CONTROLLER));
            entries.add(Item.fromBlock(ModBlocks.CONDUIT));
            entries.add(Item.fromBlock(ModBlocks.POWER_RELAY));
            entries.add(Item.fromBlock(ModBlocks.FUEL_TANK));
            entries.add(ModItems.ANOMALY_MARKER_TOOL);
            entries.add(Item.fromBlock(ModBlocks.CHARACTER_CREATION_TERMINAL));
            entries.add(ModItems.RP_PADD);
            entries.add(Item.fromBlock(ModBlocks.SUBMISSION_BOX));
            entries.add(ModItems.SPAWN_ITEM_TOOL);
            entries.add(ModItems.SPAWN_BLOCK_CONFIG_TOOL);
            entries.add(ModItems.TRICORDER);
            entries.add(ModItems.MEDICAL_PAD);
            entries.add(ModItems.GURNEY);
        });

        ItemGroup secondDawnRPGroup = Registry.register(
                Registries.ITEM_GROUP,
                Identifier.of(SecondDawnRP.MOD_ID, "00seconddawnrp"),
                FabricItemGroup.builder()
                        .displayName(Text.literal("SecondDawnRP").formatted(Formatting.GOLD))
                        .icon(() -> new ItemStack(ModItems.SPAWN_ITEM_TOOL))
                        .entries((context, entries) -> {
                            entries.add(ModItems.TASK_PAD);
                            entries.add(ModItems.OPERATIONS_PAD);
                            entries.add(ModItems.TASK_TERMINAL_TOOL);
                            entries.add(ModItems.TERMINAL_DESIGNATOR_TOOL);
                            entries.add(ModItems.ENGINEERING_PAD);
                            entries.add(ModItems.COMPONENT_REGISTRATION_TOOL);
                            entries.add(ModItems.WARP_CORE_TOOL);
                            entries.add(ModItems.FUEL_ROD);
                            entries.add(ModItems.CONTAINMENT_CELL);
                            entries.add(ModItems.RESONANCE_COIL);
                            entries.add(ModItems.ENVIRONMENTAL_EFFECT_TOOL);
                            entries.add(ModItems.TRIGGER_TOOL);
                            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_CASING));
                            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_INJECTOR));
                            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_COLUMN));
                            entries.add(Item.fromBlock(ModBlocks.WARP_CORE_CONTROLLER));
                            entries.add(Item.fromBlock(ModBlocks.CONDUIT));
                            entries.add(Item.fromBlock(ModBlocks.POWER_RELAY));
                            entries.add(Item.fromBlock(ModBlocks.FUEL_TANK));
                            entries.add(ModItems.ANOMALY_MARKER_TOOL);
                            entries.add(Item.fromBlock(ModBlocks.CHARACTER_CREATION_TERMINAL));
                            entries.add(ModItems.RP_PADD);
                            entries.add(Item.fromBlock(ModBlocks.SUBMISSION_BOX));
                            entries.add(ModItems.SPAWN_ITEM_TOOL);
                            entries.add(ModItems.SPAWN_BLOCK_CONFIG_TOOL);
                            entries.add(ModItems.ROSTER_PAD);
                            entries.add(ModItems.TRICORDER);
                            entries.add(ModItems.MEDICAL_PAD);
                            entries.add(ModItems.GURNEY);
                            entries.add(ModItems.DAMAGE_ZONE_TOOL);
                            entries.add(Item.fromBlock(ModBlocks.TACTICAL_CONSOLE));

                        })
                        .build()
        );

        Path configDir = Path.of("config");

        DatabaseConfig databaseConfig = new DatabaseConfig(configDir);
        DATABASE_MANAGER = new DatabaseManager(databaseConfig);
        try {
            DATABASE_MANAGER.init();
            new DatabaseBootstrap(DATABASE_MANAGER, new DatabaseMigrations()).bootstrap();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database infrastructure", e);
        }
        ServiceRecordRepository serviceRecordRepository =
                new ServiceRecordRepository(DATABASE_MANAGER);
        SERVICE_RECORD_SERVICE = new ServiceRecordService(serviceRecordRepository);
        ProfileRepository profileRepository = new SqlProfileRepository(DATABASE_MANAGER);
        try {
            net.shard.seconddawnrp.tactical.data.TacticalMigration
                    .applyVersion13(DATABASE_MANAGER.getConnection()); // V13
            net.shard.seconddawnrp.tactical.data.TacticalMigration
                    .applyVersion14(DATABASE_MANAGER.getConnection()); // V14 — ship_position table
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to apply tactical migrations", e);
        }
        net.shard.seconddawnrp.tactical.data.ShipClassDefinition
                .loadAll(Path.of("data"));

        net.shard.seconddawnrp.tactical.data.TacticalRepository tacticalRepository =
                new net.shard.seconddawnrp.tactical.data.TacticalRepository(DATABASE_MANAGER);
        ENCOUNTER_SERVICE = new net.shard.seconddawnrp.tactical.service.EncounterService(tacticalRepository);
        TACTICAL_SERVICE = new net.shard.seconddawnrp.tactical.service.TacticalService(
                ENCOUNTER_SERVICE, tacticalRepository);
// Phase 12 — Passive ship movement
        var passiveShipRepo = new net.shard.seconddawnrp.tactical.data.SqlShipPositionRepository(DATABASE_MANAGER);
        PASSIVE_SHIP_MOVEMENT_SERVICE = new net.shard.seconddawnrp.tactical.data.PassiveShipMovementService(passiveShipRepo);

        net.shard.seconddawnrp.tactical.network.TacticalNetworking.registerPayloads();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(LocateZoneBlockS2CPacket.ID, LocateZoneBlockS2CPacket.CODEC);
        JsonTaskStateRepository jsonTaskStateRepository = new JsonTaskStateRepository(configDir);
        try { jsonTaskStateRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON task state backup", e); }
        TaskStateRepository taskStateRepository = new SqlTaskStateRepository(DATABASE_MANAGER);

        JsonOpsTaskPoolRepository jsonOpsTaskPoolRepository = new JsonOpsTaskPoolRepository(configDir);
        try { jsonOpsTaskPoolRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON ops pool backup", e); }
        OpsTaskPoolRepository opsTaskPoolRepository = new SqlOpsTaskPoolRepository(DATABASE_MANAGER);

        DefaultProfileFactory defaultProfileFactory = new DefaultProfileFactory();
        PROFILE_MANAGER = new PlayerProfileManager(profileRepository, defaultProfileFactory);
        DOWNED_SERVICE = new DownedService(PROFILE_MANAGER);
        PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, new NoOpProfileSyncService());
        PERMISSION_SERVICE = new NoOpPermissionService();
        TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);
        TaskRegistry.bootstrap();
        TASK_REWARD_SERVICE = new TaskRewardService();
        TASK_SERVICE = new TaskService(PROFILE_MANAGER, TASK_REWARD_SERVICE,
                taskStateRepository, opsTaskPoolRepository);

        ModNetworking.registerC2SPackets();
        TaskEventRegistrar.register(PROFILE_MANAGER, TASK_SERVICE);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PlayerProfileCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TaskCommands.register(dispatcher, PROFILE_MANAGER, TASK_SERVICE));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                net.shard.seconddawnrp.tactical.command.TacticalCommands.register(dispatcher, TACTICAL_SERVICE));
        JsonTaskTerminalRepository jsonTaskTerminalRepository = new JsonTaskTerminalRepository(configDir);
        try { jsonTaskTerminalRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON terminal backup", e); }
        TaskTerminalRepository taskTerminalRepository = new SqlTerminalRepository(DATABASE_MANAGER);
        TERMINAL_MANAGER = new TaskTerminalManager(taskTerminalRepository, TASK_SERVICE, PROFILE_MANAGER);
        new TerminalInteractListener(TERMINAL_MANAGER).register();

        JsonEncounterTemplateRepository templateRepo = new JsonEncounterTemplateRepository(configDir);
        try { templateRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize encounter template repository", e); }

        JsonSpawnBlockRepository spawnBlockRepo = new JsonSpawnBlockRepository(configDir);
        try { spawnBlockRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize spawn block repository", e); }

        GmEventConfigRepository gmConfigRepo = new GmEventConfigRepository(configDir);
        try { gmConfigRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize GM event config", e); }
        GmEventConfig gmEventConfig = gmConfigRepo.load();

        GM_PERMISSION_SERVICE = new GmPermissionService(PERMISSION_SERVICE);
        GM_EVENT_SERVICE = new GmEventService(templateRepo, spawnBlockRepo, TASK_SERVICE, gmEventConfig);

        new MobDeathEventListener(GM_EVENT_SERVICE).register();
        new GmDamageListener(GM_EVENT_SERVICE).register();
        new GmMobHitListener().register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmEventCommands.register(dispatcher));

        DegradationConfigRepository degradationConfigRepo = new DegradationConfigRepository(configDir);
        try { degradationConfigRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize degradation config", e); }
        DegradationConfig degradationConfig = degradationConfigRepo.load();

        JsonComponentRepository componentRepository = new JsonComponentRepository(configDir);
        try { componentRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize component repository", e); }

        DEGRADATION_SERVICE = new DegradationService(componentRepository, TASK_SERVICE, degradationConfig);

        DegradationNetworking.registerPayloads();
        new ComponentInteractListener().register();
        new ComponentNamingChatListener().register();
        new ComponentDamageListener().register();
        new ComponentBlockBreakListener().register();
        ComponentInteractionBlocker.register();
        net.shard.seconddawnrp.tactical.damage.ZoneRepairListener.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EngineeringCommands.register(dispatcher, registryAccess, environment));

        WarpCoreConfigRepository warpCoreConfigRepo = new WarpCoreConfigRepository(configDir);
        try { warpCoreConfigRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize warp core config", e); }
        WarpCoreConfig warpCoreConfig = warpCoreConfigRepo.load();

        JsonWarpCoreRepository warpCoreRepository = new JsonWarpCoreRepository(configDir);
        try { warpCoreRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize warp core repository", e); }

        WARP_CORE_SERVICE = new WarpCoreService(warpCoreRepository, warpCoreConfig);
        WarpCoreNetworking.registerPayloads();
        WarpCoreNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WarpCoreCommands.register(dispatcher, registryAccess, environment));

        CHARACTER_ARCHIVE = new CharacterArchiveRepository(DATABASE_MANAGER);
        SPECIES_REGISTRY = new SpeciesRegistry();

        SqlLongTermInjuryRepository sqlLtiRepository = new SqlLongTermInjuryRepository(DATABASE_MANAGER);

        // ── SINGLE MedicalConditionRegistry instance shared by ALL services ──
        // This MUST be created once here. Do NOT create it again in the Phase 8
        // block below — doing so creates two instances, and only the second one
        // gets reload() called, leaving LongTermInjuryService with an empty registry.
        MEDICAL_CONDITION_REGISTRY = new MedicalConditionRegistry();

        LONG_TERM_INJURY_SERVICE = new LongTermInjuryService(
                sqlLtiRepository,
                (playerUuid, conditionIds) -> {
                    PlayerProfile p = PROFILE_MANAGER.getLoadedProfile(playerUuid);
                    if (p == null) return;
                    p.clearMedicalConditionIds();
                    conditionIds.forEach(p::addMedicalConditionId);
                    PROFILE_MANAGER.markDirty(playerUuid);
                },
                MEDICAL_CONDITION_REGISTRY
        );

        GURNEY_SERVICE = new GurneyService(PROFILE_MANAGER);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GurneyCommands.register(dispatcher));

        RDM_DETECTION_SERVICE = new RdmDetectionService(DATABASE_MANAGER);

        CharacterCreationNetworking.registerPayloads();
        CharacterCreationNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmCharacterCommands.register(dispatcher));

        RollModifierConfig rollModifierConfig = new RollModifierConfig(configDir);

        RP_PADD_SERVICE = new RpPaddService();
        ROLL_SERVICE    = new RollService(rollModifierConfig);

        RpPaddSubmission.Repository submissionRepo = new RpPaddSubmission.Repository(DATABASE_MANAGER);
        RP_PADD_SUBMISSION_SERVICE = new RpPaddSubmissionService(submissionRepo);

        RpPaddNetworking.registerPayloads();
        RpPaddNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RollCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmRollCommands.register(dispatcher));

        GM_REGISTRY_SERVICE = new GmRegistryService(configDir);

        JsonEnvironmentalEffectRepository envRepo = new JsonEnvironmentalEffectRepository(configDir);
        try { envRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize env effect repository", e); }
        ENV_EFFECT_SERVICE = new EnvironmentalEffectService(envRepo);

        GmEventNetworking.registerPayloads();
        GmEventNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmEnvCommands.register(dispatcher, registryAccess, environment));

        JsonTriggerRepository triggerRepo = new JsonTriggerRepository(configDir);
        try { triggerRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize trigger repository", e); }
        TRIGGER_SERVICE = new TriggerService(triggerRepo);

        JsonAnomalyRepository anomalyRepo = new JsonAnomalyRepository(configDir);
        try { anomalyRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize anomaly repository", e); }
        ANOMALY_SERVICE = new AnomalyService(anomalyRepo);
        GM_TOOL_VISIBILITY_SERVICE = new GmToolVisibilityService();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmAnomalyCommands.register(dispatcher, registryAccess, environment));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmTriggerCommands.register(dispatcher, registryAccess, environment));

        PayloadTypeRegistry.playS2C().register(OpenAnomalyConfigS2CPacket.ID, OpenAnomalyConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveAnomalyConfigC2SPacket.ID, SaveAnomalyConfigC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(LocateComponentS2CPacket.ID, LocateComponentS2CPacket.CODEC);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            String wk = world.getRegistryKey().getValue().toString();
            long pos = hitResult.getBlockPos().asLong();
            return TRIGGER_SERVICE.getByPosition(wk, pos)
                    .filter(e -> e.getTriggerMode() == TriggerMode.INTERACT)
                    .map(e -> { TRIGGER_SERVICE.fireInteract(e, sp); return ActionResult.PASS; })
                    .orElse(ActionResult.PASS);
        });



        // ── Phase 5.25 — Terminal Designator ─────────────────────────────────

        TERMINAL_DESIGNATOR_REGISTRY = new TerminalDesignatorRegistry(configDir);
        try { TERMINAL_DESIGNATOR_REGISTRY.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize terminal designator registry", e); }
        TERMINAL_DESIGNATOR_SERVICE = new TerminalDesignatorService();
        new TerminalDesignatorInteractListener().register();

        // ── Phase 5.5 — Career path infrastructure ────────────────────────────

        CadetRankConfig cadetRankConfig = new CadetRankConfig(configDir);
        try { cadetRankConfig.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to init cadet config", e); }

        OfficerSlotConfig officerSlotConfig = new OfficerSlotConfig(configDir);
        try { officerSlotConfig.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to init officer slot config", e); }

        OfficerProgressionConfig officerProgressionConfig = new OfficerProgressionConfig(configDir);
        try { officerProgressionConfig.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to init officer progression config", e); }

        SqlSlotQueueRepository slotQueueRepo = new SqlSlotQueueRepository(DATABASE_MANAGER);

        CADET_SERVICE               = new CadetService(PROFILE_MANAGER, cadetRankConfig);
        OFFICER_SLOT_SERVICE        = new OfficerSlotService(officerSlotConfig, slotQueueRepo, PROFILE_MANAGER);
        OFFICER_PROGRESSION_SERVICE = new OfficerProgressionService(PROFILE_MANAGER, officerProgressionConfig);
        COMMENDATION_SERVICE        = new CommendationService(PROFILE_MANAGER, officerProgressionConfig);
        GROUP_TASK_SERVICE          = new GroupTaskService(PROFILE_MANAGER);
        ROSTER_SERVICE = new net.shard.seconddawnrp.roster.service.RosterService(PROFILE_MANAGER);
        net.shard.seconddawnrp.roster.network.RosterNetworking.registerPayloads();
        net.shard.seconddawnrp.roster.network.RosterNetworking.registerServerReceivers();
        COMMENDATION_SERVICE.setServiceRecordService(SERVICE_RECORD_SERVICE);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ProgressionCommands.register(dispatcher));

        // ── Phase 8 — Medical ─────────────────────────────────────────────────
        // NOTE: MEDICAL_CONDITION_REGISTRY already created above — not created again here.

        SqlMedicalTerminalRepository medicalTerminalRepo =
                new SqlMedicalTerminalRepository(DATABASE_MANAGER);

        MedicalRepository medicalRepo =
                new SqlMedicalRepository(DATABASE_MANAGER, sqlLtiRepository);

        MEDICAL_TERMINAL_SERVICE = new MedicalTerminalService(
                medicalTerminalRepo,
                PROFILE_MANAGER,
                medicalRepo,
                MEDICAL_CONDITION_REGISTRY
        );

        MEDICAL_SERVICE = new MedicalService(
                medicalRepo,
                MEDICAL_CONDITION_REGISTRY,
                PROFILE_MANAGER,
                MEDICAL_TERMINAL_SERVICE,
                LONG_TERM_INJURY_SERVICE
        );

        new MedicalInteractListener().register();
        net.shard.seconddawnrp.medical.network.MedicalPadNetworking.registerPayloads();
        net.shard.seconddawnrp.medical.network.MedicalPadNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmMedicalCommands.register(dispatcher));

        DownedHooks.register();

        LOCATION_REGISTRY = new LocationRegistry();
        LOCATION_REGISTRY.reload();

        LOCATION_SERVICE = new LocationService(LOCATION_REGISTRY, DATABASE_MANAGER);

        TRANSPORTER_SERVICE = new TransporterService(DATABASE_MANAGER, LOCATION_SERVICE);
        TransporterControllerNetworking.registerPayloads();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmLocationCommands.register(dispatcher, LOCATION_SERVICE));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AdminTransporterCommands.register(dispatcher, TRANSPORTER_SERVICE));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TransporterCommands.register(dispatcher, TRANSPORTER_SERVICE, PROFILE_MANAGER));// ── Tick loop ─────────────────────────────────────────────────────────

        ServerTickEvents.END_SERVER_TICK.register(server -> GM_EVENT_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> DEGRADATION_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> WARP_CORE_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> ENV_EFFECT_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> GURNEY_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                DOWNED_SERVICE.tick(server, server.getTicks()));
        ServerTickEvents.END_SERVER_TICK.register(server -> TRIGGER_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                LONG_TERM_INJURY_SERVICE.tick(server, server.getTicks()));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                ROLL_SERVICE.tick(server, server.getTicks()));


        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 200 != 0) return; // every 10s
            for (var player : server.getPlayerManager().getPlayerList()) {
                TacticalNetworking.sendStandbyUpdate(player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server ->
                TACTICAL_SERVICE.tick(server, server.getTicks()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 1_728_000 == 0 && server.getTicks() > 0) {
                RP_PADD_SUBMISSION_SERVICE.cleanup();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTicks() % 600 != 0) return;
            if (PASSIVE_SHIP_MOVEMENT_SERVICE != null) {
                PASSIVE_SHIP_MOVEMENT_SERVICE.onPassiveTick(srv);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTicks() % 200 != 0) return;
            for (var player : srv.getPlayerManager().getPlayerList()) {
                net.shard.seconddawnrp.tactical.network.TacticalNetworking.sendStandbyUpdate(player);
            }
        });

        // ── SERVER_STARTED ────────────────────────────────────────────────────

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PROFILE_SERVICE.setProfileSyncService(new NoOpProfileSyncService());
            PERMISSION_SERVICE = new NoOpPermissionService();
            TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);

            if (FabricLoader.getInstance().isModLoaded("luckperms")) {
                try {
                    Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                    Object luckPerms = providerClass.getMethod("get").invoke(null);
                    LuckPermsGroupMapper groupMapper = new LuckPermsGroupMapper();
                    ProfileSyncService syncService = LuckPermsSyncService.create(luckPerms, groupMapper);
                    PROFILE_SERVICE.setProfileSyncService(syncService);
                    PERMISSION_SERVICE = LuckPermsPermissionService.create(luckPerms);
                    TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);
                    System.out.println("[SecondDawnRP] LuckPerms integration initialized.");
                } catch (Throwable t) {
                    System.out.println("[SecondDawnRP] LuckPerms detected but unavailable - continuing without LP sync.");
                    t.printStackTrace();
                }
            } else {
                System.out.println("[SecondDawnRP] LuckPerms not installed - continuing without LP sync.");
            }

            try { TaskJsonLoader.load(server.getResourceManager()); }
            catch (Exception e) { System.out.println("[SecondDawnRP] Failed to load task JSON."); }

            TERMINAL_MANAGER.reload();
            GmEventService.setServer(server);
            DEGRADATION_SERVICE.setServer(server);
            DEGRADATION_SERVICE.reload();
            WARP_CORE_SERVICE.setServer(server);
            WARP_CORE_SERVICE.reload();
            ENV_EFFECT_SERVICE.reload();
            GM_REGISTRY_SERVICE.reload();
            TRIGGER_SERVICE.reload();
            ANOMALY_SERVICE.reload();
            SPECIES_REGISTRY.reload();
            LONG_TERM_INJURY_SERVICE.setServer(server);
            LONG_TERM_INJURY_SERVICE.reload();
            RDM_DETECTION_SERVICE.setServer(server);
            rollModifierConfig.load();
            ROLL_SERVICE.setServer(server);
            RP_PADD_SUBMISSION_SERVICE.setServer(server);
            ENCOUNTER_SERVICE.setServer(server);
            TACTICAL_SERVICE.setServer(server);
            if (PASSIVE_SHIP_MOVEMENT_SERVICE != null) {
                PASSIVE_SHIP_MOVEMENT_SERVICE.loadHomeShip();
            }
            ENCOUNTER_SERVICE.loadFromDatabase();
            TACTICAL_SERVICE.loadFromDatabase();
            net.shard.seconddawnrp.tactical.network.TacticalNetworking.registerServerReceivers();

            // Phase 5.25
            TERMINAL_DESIGNATOR_REGISTRY.reload();
            net.shard.seconddawnrp.cc.CCPeripheralRegistry.register(server);

            // Phase 5.5
            cadetRankConfig.load();
            officerSlotConfig.load();
            officerProgressionConfig.load();
            CADET_SERVICE.setServer(server);
            OFFICER_SLOT_SERVICE.setServer(server);
            OFFICER_PROGRESSION_SERVICE.setServer(server);
            COMMENDATION_SERVICE.setServer(server);
            if (SHIP_POSITION_SERVICE != null) {
                SHIP_POSITION_SERVICE.setServer(server);
            }
            GROUP_TASK_SERVICE.setServer(server);
            ROSTER_SERVICE.setServer(server);

            // Phase 8 — Medical
            // reload() populates the SINGLE shared MEDICAL_CONDITION_REGISTRY instance
            MEDICAL_CONDITION_REGISTRY.reload();
            MEDICAL_TERMINAL_SERVICE.reload();
            MEDICAL_SERVICE.setServer(server);
            DOWNED_SERVICE.setServer(server);

            LOCATION_SERVICE.setServer(server);
            LOCATION_SERVICE.loadFromDatabase();

            TRANSPORTER_SERVICE.setServer(server);
            TRANSPORTER_SERVICE.loadFromDatabase();

            TransporterControllerNetworking.registerServerReceivers(
                    TRANSPORTER_SERVICE, LOCATION_SERVICE);
        });

        // ── Player join / leave ───────────────────────────────────────────────

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerProfile profile = PROFILE_SERVICE.getOrLoad(handler.getPlayer());
            TASK_SERVICE.loadTaskState(profile);
            PROFILE_SERVICE.syncAll(handler.getPlayer());
            LONG_TERM_INJURY_SERVICE.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerUuid = handler.getPlayer().getUuid();
            PlayerProfile profile = PROFILE_MANAGER.getLoadedProfile(playerUuid);
            if (profile != null) TASK_SERVICE.saveTaskState(profile);
            LONG_TERM_INJURY_SERVICE.onPlayerLeave(playerUuid);
            PROFILE_MANAGER.unloadProfile(playerUuid);
            if (TRANSPORTER_SERVICE != null) {
                TRANSPORTER_SERVICE.clearReady(handler.player.getUuid());
            }
        });

        // ── SERVER_STOPPING ───────────────────────────────────────────────────

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (GURNEY_SERVICE != null) {
                GURNEY_SERVICE.cleanupAll(server);
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerProfile profile = PROFILE_MANAGER.getLoadedProfile(player.getUuid());
                if (profile != null) TASK_SERVICE.saveTaskState(profile);
            }
            WARP_CORE_SERVICE.save();
            ENV_EFFECT_SERVICE.saveAll();
            TRIGGER_SERVICE.saveAll();
            DEGRADATION_SERVICE.saveAll();
            PROFILE_MANAGER.saveAll();
            ANOMALY_SERVICE.saveAll();
            TERMINAL_DESIGNATOR_REGISTRY.saveAll();
            if (DATABASE_MANAGER != null) {
                try { DATABASE_MANAGER.close(); }
                catch (Exception e) { throw new RuntimeException("Failed to close database manager", e); }
            }
            ServerTickEvents.END_SERVER_TICK.register(srv -> {
                if (server.getTicks() % 200 != 0) return;
                for (var player : server.getPlayerManager().getPlayerList()) {
                    net.shard.seconddawnrp.tactical.network.TacticalNetworking.sendStandbyUpdate(player);
                }
            });

            ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
                if (PASSIVE_SHIP_MOVEMENT_SERVICE != null) {
                    PASSIVE_SHIP_MOVEMENT_SERVICE.savePosition();
                }
            });


        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}