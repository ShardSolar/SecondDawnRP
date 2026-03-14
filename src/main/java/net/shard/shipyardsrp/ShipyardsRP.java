package net.shard.shipyardsrp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.item.ItemGroups;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.shipyardsrp.database.DatabaseBootstrap;
import net.shard.shipyardsrp.database.DatabaseConfig;
import net.shard.shipyardsrp.database.DatabaseManager;
import net.shard.shipyardsrp.database.DatabaseMigrations;
import net.shard.shipyardsrp.registry.ModItems;
import net.shard.shipyardsrp.registry.ModScreenHandlers;
import net.shard.shipyardsrp.starfleetarchives.*;
import net.shard.shipyardsrp.starfleetarchives.persistence.JsonProfileRepository;
import net.shard.shipyardsrp.starfleetarchives.persistence.ProfileRepository;
import net.shard.shipyardsrp.starfleetarchives.persistence.SqlProfileRepository;
import net.shard.shipyardsrp.tasksystem.command.TaskCommands;
import net.shard.shipyardsrp.tasksystem.event.TaskEventRegistrar;
import net.shard.shipyardsrp.tasksystem.loader.TaskJsonLoader;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;
import net.shard.shipyardsrp.tasksystem.repository.JsonTaskStateRepository;
import net.shard.shipyardsrp.tasksystem.repository.TaskStateRepository;
import net.shard.shipyardsrp.tasksystem.service.TaskRewardService;
import net.shard.shipyardsrp.tasksystem.service.TaskService;

import java.nio.file.Path;

public class ShipyardsRP implements ModInitializer {

    public static final String MOD_ID = "shipyardsrp";

    public static DatabaseManager DATABASE_MANAGER;

    public static PlayerProfileManager PROFILE_MANAGER;
    public static PlayerProfileService PROFILE_SERVICE;
    public static PermissionService PERMISSION_SERVICE;
    public static TaskRewardService TASK_REWARD_SERVICE;
    public static TaskService TASK_SERVICE;

    @Override
    public void onInitialize() {
        ModItems.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ModItems.TASK_PADD);
        });

        ModScreenHandlers.register();

        Path configDir = Path.of("config");

        // Phase 2 database bootstrap only
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

        DefaultProfileFactory defaultProfileFactory = new DefaultProfileFactory();
        PROFILE_MANAGER = new PlayerProfileManager(profileRepository, defaultProfileFactory);

        PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, new NoOpProfileSyncService());
        PERMISSION_SERVICE = new PermissionService(null);

        TaskRegistry.bootstrap();
        TASK_REWARD_SERVICE = new TaskRewardService();
        TASK_SERVICE = new TaskService(PROFILE_MANAGER, TASK_REWARD_SERVICE, taskStateRepository);

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

                System.out.println("[ShipyardsRP] LuckPerms integration initialized.");
            } catch (Exception e) {
                System.out.println("[ShipyardsRP] LuckPerms not ready in this environment. Continuing without LP sync.");
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
    }
}