package net.shard.seconddawnrp.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;
import net.shard.seconddawnrp.warpcore.service.WarpCoreService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ComputerCraft peripheral — exposes WarpCoreService to Lua.
 * Peripheral type: "warpcore"
 *
 * Methods are exposed via @LuaFunction — CC discovers them automatically.
 * No getMethodNames() / callMethod() needed.
 *
 * Read methods: no permission check.
 * Write methods: resolve playerUuid → online player, then call service
 * (WarpCoreService enforces engineering.reactor permission internally).
 *
 * Lua example:
 *   local core = peripheral.find("warpcore")
 *   print(core.getState())          -- "ONLINE"
 *   print(core.getFuelLevel())      -- 0.87
 *   core.startup("player-uuid")    -- true/false
 */
public class WarpCorePeripheral implements IPeripheral {

    private final String coreId;
    private final MinecraftServer server;

    public WarpCorePeripheral(String coreId, MinecraftServer server) {
        this.coreId = coreId;
        this.server = server;
    }

    // ── IPeripheral ───────────────────────────────────────────────────────────

    @Override
    public String getType() { return "warpcore"; }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof WarpCorePeripheral wp && wp.coreId.equals(this.coreId);
    }

    // ── Lua methods ───────────────────────────────────────────────────────────

    @LuaFunction
    public final String getState() throws LuaException {
        return entry().getState().name();
    }

    @LuaFunction
    public final int getPowerOutput() throws LuaException {
        return entry().getCurrentPowerOutput();
    }

    @LuaFunction
    public final double getFuelLevel() throws LuaException {
        WarpCoreEntry e = entry();
        int max = SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods();
        return max > 0 ? (double) e.getFuelRods() / max : 0.0;
    }

    @LuaFunction
    public final double getStability() throws LuaException {
        int h = SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(entry());
        return h < 0 ? 1.0 : h / 100.0;
    }

    @LuaFunction
    public final int getCoilCount() throws LuaException {
        return entry().getResonanceCoilIds().size();
    }

    @LuaFunction
    public final double getCoilHealth() throws LuaException {
        int h = SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(entry());
        return h < 0 ? 1.0 : h / 100.0;
    }

    @LuaFunction
    public final Map<String, Object> getAllCoilHealth() throws LuaException {
        Map<String, Object> table = new HashMap<>();
        for (String coilId : entry().getResonanceCoilIds()) {
            SecondDawnRP.DEGRADATION_SERVICE.getById(coilId)
                    .ifPresent(comp -> table.put(coilId, comp.getHealth() / 100.0));
        }
        return table;
    }

    @LuaFunction
    public final Map<Integer, Object> listCores() {
        Map<Integer, Object> table = new HashMap<>();
        int i = 1;
        for (WarpCoreEntry e : SecondDawnRP.WARP_CORE_SERVICE.getAll()) {
            table.put(i++, e.getEntryId());
        }
        return table;
    }

    @LuaFunction
    public final boolean startup(String playerUuid) throws LuaException {
        ServerPlayerEntity player = resolvePlayer(playerUuid);
        return SecondDawnRP.WARP_CORE_SERVICE.initiateStartup(coreId, player);
    }

    @LuaFunction
    public final boolean shutdown(String playerUuid) throws LuaException {
        ServerPlayerEntity player = resolvePlayer(playerUuid);
        return SecondDawnRP.WARP_CORE_SERVICE.initiateShutdown(coreId, player);
    }

    @LuaFunction
    public final boolean reset(String playerUuid) throws LuaException {
        ServerPlayerEntity player = resolvePlayer(playerUuid);
        return SecondDawnRP.WARP_CORE_SERVICE.resetFromFailed(coreId, player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WarpCoreEntry entry() throws LuaException {
        return SecondDawnRP.WARP_CORE_SERVICE.getById(coreId)
                .orElseThrow(() -> new LuaException("No warp core with id: " + coreId));
    }

    private ServerPlayerEntity resolvePlayer(String uuidStr) throws LuaException {
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); }
        catch (IllegalArgumentException e) { throw new LuaException("Invalid UUID: " + uuidStr); }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) throw new LuaException("Player not online: " + uuidStr);
        return player;
    }
}