package net.shard.seconddawnrp.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * ComputerCraft peripheral — exposes DegradationService to Lua.
 * Peripheral type: "degradation"
 *
 * All methods are read-only. Repairs happen physically in-world.
 *
 * Lua example:
 *   local deg = peripheral.find("degradation")
 *   for _, c in pairs(deg.getCriticalComponents()) do
 *     print(c.name, c.health)
 *   end
 */
public class DegradationPeripheral implements IPeripheral {

    private final MinecraftServer server;

    public DegradationPeripheral(MinecraftServer server) {
        this.server = server;
    }

    // ── IPeripheral ───────────────────────────────────────────────────────────

    @Override
    public String getType() { return "degradation"; }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof DegradationPeripheral;
    }

    // ── Lua methods ───────────────────────────────────────────────────────────

    @LuaFunction
    public final Map<Integer, Object> listComponents() {
        return toTable(SecondDawnRP.DEGRADATION_SERVICE.getAllComponents());
    }

    @LuaFunction
    public final Map<String, Object> getComponent(String componentId) throws LuaException {
        ComponentEntry c = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId)
                .orElseThrow(() -> new LuaException("Unknown component: " + componentId));
        return toRow(c);
    }

    @LuaFunction
    public final Map<Integer, Object> getCriticalComponents() {
        return toTable(SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()
                .stream().filter(c -> c.getStatus() == ComponentStatus.CRITICAL).toList());
    }

    @LuaFunction
    public final Map<Integer, Object> getOfflineComponents() {
        return toTable(SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()
                .stream().filter(c -> c.getStatus() == ComponentStatus.OFFLINE).toList());
    }

    @LuaFunction
    public final Map<String, Object> getStateSummary() {
        Collection<ComponentEntry> all = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();
        Map<String, Object> summary = new HashMap<>();
        for (ComponentStatus s : ComponentStatus.values()) {
            long count = all.stream().filter(c -> c.getStatus() == s).count();
            summary.put(s.name(), (int) count);
        }
        return summary;
    }

    @LuaFunction
    public final boolean isReactorCritical() {
        return SecondDawnRP.DEGRADATION_SERVICE.isReactorCritical();
    }

    // ── Table builders ────────────────────────────────────────────────────────

    private Map<Integer, Object> toTable(Iterable<ComponentEntry> entries) {
        Map<Integer, Object> table = new HashMap<>();
        int i = 1;
        for (ComponentEntry c : entries) table.put(i++, toRow(c));
        return table;
    }

    private Map<String, Object> toRow(ComponentEntry c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id",          c.getComponentId());
        row.put("name",        c.getDisplayName());
        row.put("state",       c.getStatus().name());
        row.put("health",      c.getHealth());
        row.put("healthPct",   c.getHealth() / 100.0);
        row.put("location",    BlockPos.fromLong(c.getBlockPosLong()).toShortString());
        row.put("world",       c.getWorldKey());
        String repairId = c.getRepairItemId();
        row.put("repairItem",  repairId != null ? repairId : "");
        row.put("repairCount", c.getRepairItemCount());
        return row;
    }
}