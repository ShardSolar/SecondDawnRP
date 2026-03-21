package net.shard.seconddawnrp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.item.ItemGroups;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.database.DatabaseBootstrap;
import net.shard.seconddawnrp.database.DatabaseConfig;
import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.database.DatabaseMigrations;
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
import net.shard.seconddawnrp.registry.ModItems;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.command.TaskCommands;
import net.shard.seconddawnrp.tasksystem.event.TaskEventRegistrar;
import net.shard.seconddawnrp.tasksystem.loader.TaskJsonLoader;
import net.shard.seconddawnrp.tasksystem.registry.TaskRegistry;
import net.shard.seconddawnrp.tasksystem.repository.JsonOpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.JsonTaskStateRepository;
import net.shard.seconddawnrp.tasksystem.repository.OpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.TaskStateRepository;
import net.shard.seconddawnrp.tasksystem.service.TaskPermissionService;
import net.shard.seconddawnrp.tasksystem.service.TaskRewardService;
import net.shard.seconddawnrp.tasksystem.service.TaskService;
import net.shard.seconddawnrp.tasksystem.network.ModNetworking;
import net.shard.seconddawnrp.tasksystem.terminal.JsonTaskTerminalRepository;
import net.shard.seconddawnrp.tasksystem.terminal.TaskTerminalManager;
import net.shard.seconddawnrp.tasksystem.terminal.TaskTerminalRepository;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalInteractListener;

import java.nio.file.Path;

public class SecondDawnRP implements ModInitializer {

    public static final String MOD_ID = "seconddawnrp";

    public static DatabaseManager DATABASE_MANAGER;

    public static PlayerProfileManager PROFILE_MANAGER;
    public static PlayerProfileService PROFILE_SERVICE;
    public static PermissionService PERMISSION_SERVICE;
    public static TaskRewardService TASK_REWARD_SERVICE;
    public static TaskService TASK_SERVICE;
    public static TaskPermissionService TASK_PERMISSION_SERVICE;
    public static TaskTerminalManager TERMINAL_MANAGER;

    @Override
    public void onInitialize() {
        ModItems.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ModItems.TASK_PAD);
            entries.add(ModItems.OPERATIONS_PAD);
        });

        ModScreenHandlers.register();

        Path configDir = Path.of("config");

        // Database bootstrap
        DatabaseConfig databaseConfig = new DatabaseConfig(configDir);
        DATABASE_MANAGER = new DatabaseManager(databaseConfig);


        try {
            DATABASE_MANAGER.init();

            DatabaseBootstrap databaseBootstrap = new DatabaseBootstrap(
                    DATABASE_MANAGER,
                    new DatabaseMigrations()
            );
            databaseBootstrap.bootstrap();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database infrastructure", e);
        }

        ProfileRepository profileRepository = new SqlProfileRepository(DATABASE_MANAGER);

        JsonTaskStateRepository jsonTaskStateRepository = new JsonTaskStateRepository(configDir);
        try {
            jsonTaskStateRepository.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize task state repository", e);
        }
        TaskStateRepository taskStateRepository = jsonTaskStateRepository;

        JsonOpsTaskPoolRepository jsonOpsTaskPoolRepository = new JsonOpsTaskPoolRepository(configDir);
        try {
            jsonOpsTaskPoolRepository.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ops task pool repository", e);
        }
        OpsTaskPoolRepository opsTaskPoolRepository = jsonOpsTaskPoolRepository;

        DefaultProfileFactory defaultProfileFactory = new DefaultProfileFactory();
        PROFILE_MANAGER = new PlayerProfileManager(profileRepository, defaultProfileFactory);

        PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, new NoOpProfileSyncService());
        PERMISSION_SERVICE = new PermissionService(null);
        TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);
        TaskRegistry.bootstrap();
        TASK_REWARD_SERVICE = new TaskRewardService();
        TASK_SERVICE = new TaskService(
                PROFILE_MANAGER,
                TASK_REWARD_SERVICE,
                taskStateRepository,
                opsTaskPoolRepository
        );

        ModNetworking.registerC2SPackets();

        TaskEventRegistrar.register(PROFILE_MANAGER, TASK_SERVICE);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PlayerProfileCommands.register(dispatcher)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TaskCommands.register(dispatcher, PROFILE_MANAGER, TASK_SERVICE)
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                TaskJsonLoader.load(server.getResourceManager());

                LuckPerms luckPerms = LuckPermsProvider.get();
                LuckPermsGroupMapper groupMapper = new LuckPermsGroupMapper();
                ProfileSyncService syncService = new LuckPermsSyncService(luckPerms, groupMapper);

                PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, syncService);
                PERMISSION_SERVICE = new PermissionService(luckPerms);
                TASK_PERMISSION_SERVICE = new TaskPermissionService(PERMISSION_SERVICE);
                System.out.println("[SecondDawnRP] LuckPerms integration initialized.");
            } catch (Exception e) {
                System.out.println("[SecondDawnRP] LuckPerms not ready in this environment. Continuing without LP sync.");
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerProfile profile = PROFILE_SERVICE.getOrLoad(handler.getPlayer());
            TASK_SERVICE.loadTaskState(profile);
            PROFILE_SERVICE.syncAll(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerProfile profile = PROFILE_MANAGER.getLoadedProfile(handler.getPlayer().getUuid());
            if (profile != null) {
                TASK_SERVICE.saveTaskState(profile);
            }
            PROFILE_MANAGER.unloadProfile(handler.getPlayer().getUuid());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerProfile profile = PROFILE_MANAGER.getLoadedProfile(player.getUuid());
                if (profile != null) {
                    TASK_SERVICE.saveTaskState(profile);
                }
            }

            PROFILE_MANAGER.saveAll();

            if (DATABASE_MANAGER != null) {
                try {
                    DATABASE_MANAGER.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to close database manager", e);
                }
            }


        });

        JsonTaskTerminalRepository jsonTaskTerminalRepository = new JsonTaskTerminalRepository(configDir);
        try {
            jsonTaskTerminalRepository.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize task terminal repository", e);
        }
        TaskTerminalRepository taskTerminalRepository = jsonTaskTerminalRepository;
        TERMINAL_MANAGER = new TaskTerminalManager(taskTerminalRepository, TASK_SERVICE, PROFILE_MANAGER);
        new TerminalInteractListener(TERMINAL_MANAGER).register();


    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}