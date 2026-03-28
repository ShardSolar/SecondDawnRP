package net.shard.seconddawnrp.cc;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Registers all SecondDawnRP ComputerCraft peripherals on SERVER_STARTED.
 *
 * CC is entirely optional. The FabricLoader.isModLoaded("computercraft") check
 * prevents any CC class from loading if CC is absent — no ClassNotFoundException,
 * no NoClassDefFoundError, no crash.
 *
 * Peripherals use @LuaFunction annotations — CC discovers methods automatically.
 * No getMethodNames() / callMethod() / IDynamicPeripheral required.
 */
public class CCPeripheralRegistry {

    private static boolean initialized = false;

    /**
     * Called from SecondDawnRP.java inside SERVER_STARTED.
     * Silently skips if CC is absent.
     */
    public static void register(MinecraftServer server) {
        if (initialized) return;

        if (!FabricLoader.getInstance().isModLoaded("computercraft")) {
            System.out.println("[SecondDawnRP] ComputerCraft not detected — CC integration skipped.");
            return;
        }

        try {
            doRegister(server);
            initialized = true;
            System.out.println("[SecondDawnRP] ComputerCraft peripheral API registered successfully.");
        } catch (Throwable t) {
            System.out.println("[SecondDawnRP] CC detected but peripheral registration failed — continuing without CC.");
            t.printStackTrace();
        }
    }

    private static void doRegister(MinecraftServer server) {
        registerWarpCorePeripheral(server);
        registerDegradationPeripheral(server);
        registerOpsPeripheral(server);
    }

    // ── Warp Core ─────────────────────────────────────────────────────────────

    private static void registerWarpCorePeripheral(MinecraftServer server) {
        try {
            PeripheralLookup.get().registerForBlockEntity(
                    (blockEntity, direction) -> {
                        if (blockEntity.getWorld() == null) return null;
                        String worldKey = blockEntity.getWorld()
                                .getRegistryKey().getValue().toString();
                        long packedPos = blockEntity.getPos().asLong();
                        return SecondDawnRP.WARP_CORE_SERVICE
                                .getByPosition(worldKey, packedPos)
                                .map(entry -> new WarpCorePeripheral(entry.getEntryId(), server))
                                .orElse(null);
                    },
                    net.shard.seconddawnrp.registry.ModBlocks.WARP_CORE_CONTROLLER_ENTITY
            );
            System.out.println("[SecondDawnRP] CC: WarpCorePeripheral registered.");
        } catch (Exception e) {
            System.out.println("[SecondDawnRP] CC: Failed to register WarpCorePeripheral — " + e.getMessage());
        }
    }

    // ── Degradation ───────────────────────────────────────────────────────────

    private static void registerDegradationPeripheral(MinecraftServer server) {
        SecondDawnRP.CC_DEGRADATION_PERIPHERAL = new DegradationPeripheral(server);
        System.out.println("[SecondDawnRP] CC: DegradationPeripheral ready.");
    }

    // ── Ops ───────────────────────────────────────────────────────────────────

    private static void registerOpsPeripheral(MinecraftServer server) {
        SecondDawnRP.CC_OPS_PERIPHERAL = new OpsPeripheral(server);
        System.out.println("[SecondDawnRP] CC: OpsPeripheral ready.");
    }
}