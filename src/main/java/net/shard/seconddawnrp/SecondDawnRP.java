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
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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
import net.shard.seconddawnrp.playerdata.DefaultProfileFactory;
import net.shard.seconddawnrp.playerdata.LuckPermsGroupMapper;
import net.shard.seconddawnrp.playerdata.LuckPermsSyncService;
import net.shard.seconddawnrp.playerdata.NoOpProfileSyncService;
import net.shard.seconddawnrp.playerdata.PermissionService;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileCommands;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.playerdata.PlayerProfileService;
import net.shard.seconddawnrp.playerdata.ProfileSyncService;
import net.shard.seconddawnrp.playerdata.persistence.ProfileRepository;
import net.shard.seconddawnrp.playerdata.persistence.SqlProfileRepository;
import net.shard.seconddawnrp.registry.ModBlocks;
import net.shard.seconddawnrp.registry.ModItems;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
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
import net.shard.seconddawnrp.warpcore.command.WarpCoreCommands;
import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;
import net.shard.seconddawnrp.warpcore.network.WarpCoreNetworking;
import net.shard.seconddawnrp.warpcore.repository.JsonWarpCoreRepository;
import net.shard.seconddawnrp.warpcore.repository.WarpCoreConfigRepository;
import net.shard.seconddawnrp.warpcore.service.WarpCoreService;

import java.nio.file.Path;
import java.util.UUID;

public class SecondDawnRP implements ModInitializer {

    public static final String MOD_ID = "seconddawnrp";

    // ── Singletons ────────────────────────────────────────────────────────────

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

    // Phase 5.5 — full implementations replacing stubs
    public static CharacterService CHARACTER_SERVICE;
    public static LongTermInjuryService LONG_TERM_INJURY_SERVICE;
    public static RdmDetectionService RDM_DETECTION_SERVICE;

    public static EnvironmentalEffectService ENV_EFFECT_SERVICE;
    public static TriggerService TRIGGER_SERVICE;
    public static GmRegistryService GM_REGISTRY_SERVICE;
    public static AnomalyService ANOMALY_SERVICE;
    public static GmToolVisibilityService GM_TOOL_VISIBILITY_SERVICE;
    public static SpeciesRegistry SPECIES_REGISTRY;

    @Override
    public void onInitialize() {

        ModItems.register();
        ModBlocks.register();
        ModScreenHandlers.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ModItems.TASK_PAD);
            entries.add(ModItems.OPERATIONS_PAD);
            entries.add(ModItems.TASK_TERMINAL_TOOL);
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
            entries.add(ModBlocks.CHARACTER_CREATION_TERMINAL);

        });

        ItemGroup SecondDawnRP = Registry.register(
                Registries.ITEM_GROUP,
                Identifier.of(net.shard.seconddawnrp.SecondDawnRP.MOD_ID, "00seconddawnrp"),
                FabricItemGroup.builder()
                        .displayName(Text.literal("SecondDawnRP").formatted(Formatting.GOLD))
                        .icon(() -> new ItemStack(ModItems.SPAWN_ITEM_TOOL))
                        .entries((context, entries) -> {
                            entries.add(ModItems.TASK_PAD);
                            entries.add(ModItems.OPERATIONS_PAD);
                            entries.add(ModItems.TASK_TERMINAL_TOOL);
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
                            entries.add(ModBlocks.CHARACTER_CREATION_TERMINAL);
                        })
                        .build()
        );

        Path configDir = Path.of("config");

        // ── Database ──────────────────────────────────────────────────────────
        DatabaseConfig databaseConfig = new DatabaseConfig(configDir);
        DATABASE_MANAGER = new DatabaseManager(databaseConfig);
        try {
            DATABASE_MANAGER.init();
            new DatabaseBootstrap(DATABASE_MANAGER, new DatabaseMigrations()).bootstrap();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database infrastructure", e);
        }

        // ── Repositories ──────────────────────────────────────────────────────
        ProfileRepository profileRepository = new SqlProfileRepository(DATABASE_MANAGER);

        JsonTaskStateRepository jsonTaskStateRepository = new JsonTaskStateRepository(configDir);
        try { jsonTaskStateRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON task state backup", e); }
        TaskStateRepository taskStateRepository = new SqlTaskStateRepository(DATABASE_MANAGER);

        JsonOpsTaskPoolRepository jsonOpsTaskPoolRepository = new JsonOpsTaskPoolRepository(configDir);
        try { jsonOpsTaskPoolRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON ops pool backup", e); }
        OpsTaskPoolRepository opsTaskPoolRepository = new SqlOpsTaskPoolRepository(DATABASE_MANAGER);

        // ── Profile and task services ─────────────────────────────────────────
        DefaultProfileFactory defaultProfileFactory = new DefaultProfileFactory();
        PROFILE_MANAGER = new PlayerProfileManager(profileRepository, defaultProfileFactory);
        PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, new NoOpProfileSyncService());
        PERMISSION_SERVICE = new PermissionService(null);
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

        // ── Terminals ─────────────────────────────────────────────────────────
        JsonTaskTerminalRepository jsonTaskTerminalRepository = new JsonTaskTerminalRepository(configDir);
        try { jsonTaskTerminalRepository.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize JSON terminal backup", e); }
        TaskTerminalRepository taskTerminalRepository = new SqlTerminalRepository(DATABASE_MANAGER);
        TERMINAL_MANAGER = new TaskTerminalManager(taskTerminalRepository, TASK_SERVICE, PROFILE_MANAGER);
        new TerminalInteractListener(TERMINAL_MANAGER).register();

        // ── GM Event System ───────────────────────────────────────────────────
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

        // ── Engineering Degradation ───────────────────────────────────────────
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


        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EngineeringCommands.register(dispatcher, registryAccess, environment));

        // ── Warp Core ─────────────────────────────────────────────────────────
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

        // ── Character System (Phase 5.5 — full implementation) ────────────────
        CharacterRepository characterRepository = new SqlCharacterRepository(DATABASE_MANAGER);
        CHARACTER_SERVICE = new CharacterService(characterRepository);

        SPECIES_REGISTRY = new SpeciesRegistry();

        CharacterCreationNetworking.registerPayloads();
        CharacterCreationNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CharacterCommand.register(dispatcher));

        LongTermInjuryRepository ltiRepository = new SqlLongTermInjuryRepository(DATABASE_MANAGER);
        LONG_TERM_INJURY_SERVICE = new LongTermInjuryService(ltiRepository, characterRepository);

        RDM_DETECTION_SERVICE = new RdmDetectionService(DATABASE_MANAGER);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmCharacterCommands.register(dispatcher));

        // ── GM Registry ───────────────────────────────────────────────────────
        GM_REGISTRY_SERVICE = new GmRegistryService(configDir);

        // ── Environmental Effect System ───────────────────────────────────────
        JsonEnvironmentalEffectRepository envRepo = new JsonEnvironmentalEffectRepository(configDir);
        try { envRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize env effect repository", e); }
        ENV_EFFECT_SERVICE = new EnvironmentalEffectService(envRepo);

        GmEventNetworking.registerPayloads();
        GmEventNetworking.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmEnvCommands.register(dispatcher, registryAccess, environment));

        // ── Trigger Block System ──────────────────────────────────────────────
        JsonTriggerRepository triggerRepo = new JsonTriggerRepository(configDir);
        try { triggerRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize trigger repository", e); }
        TRIGGER_SERVICE = new TriggerService(triggerRepo);

        // ── Anomaly Marker System ─────────────────────────────────────────────
        JsonAnomalyRepository anomalyRepo = new JsonAnomalyRepository(configDir);
        try { anomalyRepo.init(); }
        catch (Exception e) { throw new RuntimeException("Failed to initialize anomaly repository", e); }
        ANOMALY_SERVICE = new AnomalyService(anomalyRepo);
        GM_TOOL_VISIBILITY_SERVICE = new GmToolVisibilityService();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmAnomalyCommands.register(dispatcher, registryAccess, environment));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GmTriggerCommands.register(dispatcher, registryAccess, environment));

        // ── Network payloads ──────────────────────────────────────────────────
        PayloadTypeRegistry.playS2C().register(
                OpenAnomalyConfigS2CPacket.ID, OpenAnomalyConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                SaveAnomalyConfigC2SPacket.ID, SaveAnomalyConfigC2SPacket.CODEC);
        AnomalyClientHandler.registerServerReceiver();
        PayloadTypeRegistry.playS2C().register(
                LocateComponentS2CPacket.ID, LocateComponentS2CPacket.CODEC);

        // INTERACT mode trigger right-click hook
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

        // ── Server tick ───────────────────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> GM_EVENT_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> DEGRADATION_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> WARP_CORE_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> ENV_EFFECT_SERVICE.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> TRIGGER_SERVICE.tick(server));

        // LTI tick — passes server tick count for interval check
        ServerTickEvents.END_SERVER_TICK.register(server ->
                LONG_TERM_INJURY_SERVICE.tick(server, server.getTicks()));

        // GM tool visibility poll every 10 ticks
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (var player : server.getPlayerManager().getPlayerList()) {
                GM_TOOL_VISIBILITY_SERVICE.onEquip(player, player.getMainHandStack());
            }
        });

        // ── Server started ────────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                LuckPermsGroupMapper groupMapper = new LuckPermsGroupMapper();
                ProfileSyncService syncService = new LuckPermsSyncService(luckPerms, groupMapper);
                PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, syncService);
                PERMISSION_SERVICE = new PermissionService(luckPerms);
                TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);
                System.out.println("[SecondDawnRP] LuckPerms integration initialized.");
            } catch (Exception e) {
                System.out.println("[SecondDawnRP] LuckPerms not ready - continuing without LP sync.");
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

            // Phase 5.5 — character services
            LONG_TERM_INJURY_SERVICE.setServer(server);
            LONG_TERM_INJURY_SERVICE.reload(); // loads all active LTIs into cache

            RDM_DETECTION_SERVICE.setServer(server);
        });

        // ── Player join ───────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerProfile profile = PROFILE_SERVICE.getOrLoad(handler.getPlayer());
            TASK_SERVICE.loadTaskState(profile);
            PROFILE_SERVICE.syncAll(handler.getPlayer());

            // Phase 5.5: full persistence — replaces getOrCreate stub
            CHARACTER_SERVICE.onPlayerJoin(handler.getPlayer());
            LONG_TERM_INJURY_SERVICE.onPlayerJoin(handler.getPlayer());
        });

        // ── Player disconnect ─────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerUuid = handler.getPlayer().getUuid();

            PlayerProfile profile = PROFILE_MANAGER.getLoadedProfile(playerUuid);
            if (profile != null) {
                TASK_SERVICE.saveTaskState(profile);
            }

            CHARACTER_SERVICE.onPlayerLeave(playerUuid);
            LONG_TERM_INJURY_SERVICE.onPlayerLeave(playerUuid);
            PROFILE_MANAGER.unloadProfile(playerUuid);
        });

        // ── Server stopping ───────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
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
            CHARACTER_SERVICE.saveAll(); // Phase 5.5

            if (DATABASE_MANAGER != null) {
                try {
                    DATABASE_MANAGER.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to close database manager", e);
                }
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}