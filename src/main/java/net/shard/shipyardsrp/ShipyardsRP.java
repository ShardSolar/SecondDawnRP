package net.shard.shipyardsrp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.item.ItemGroups;
import net.shard.shipyardsrp.registry.ModItems;
import net.shard.shipyardsrp.registry.ModScreenHandlers;
import net.shard.shipyardsrp.starfleetarchives.*;
import net.shard.shipyardsrp.tasksystem.command.TaskCommands;
import net.shard.shipyardsrp.tasksystem.event.TaskEventRegistrar;
import net.shard.shipyardsrp.tasksystem.loader.TaskJsonLoader;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;
import net.shard.shipyardsrp.tasksystem.service.TaskRewardService;
import net.shard.shipyardsrp.tasksystem.service.TaskService;

import java.nio.file.Path;

public class ShipyardsRP implements ModInitializer {
    public static final String MOD_ID = "shipyardsrp";
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


        ProfilePaths profilePaths = new ProfilePaths(configDir);
        ProfileSerializer serializer = new ProfileSerializer();
        PlayerProfileRepository repository = new PlayerProfileRepository(profilePaths, serializer);

        try {
            repository.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize profile repository", e);
        }

        DefaultProfileFactory defaultProfileFactory = new DefaultProfileFactory();
        PROFILE_MANAGER = new PlayerProfileManager(repository, defaultProfileFactory);

        // Start with no-op sync so singleplayer/dev can still run.
        PROFILE_SERVICE = new PlayerProfileService(PROFILE_MANAGER, new NoOpProfileSyncService());
        PERMISSION_SERVICE = new PermissionService(null);

        // Task system init
        TaskRegistry.bootstrap();
        TASK_REWARD_SERVICE = new TaskRewardService();
        TASK_SERVICE = new TaskService(PROFILE_MANAGER, TASK_REWARD_SERVICE);
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
            PROFILE_SERVICE.getOrLoad(handler.getPlayer());
            PROFILE_SERVICE.syncAll(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PROFILE_MANAGER.unloadProfile(handler.getPlayer().getUuid());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PROFILE_MANAGER.saveAll();
        });
    }



}