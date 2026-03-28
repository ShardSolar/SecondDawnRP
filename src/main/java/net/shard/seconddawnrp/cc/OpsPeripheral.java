package net.shard.seconddawnrp.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ComputerCraft peripheral — exposes the Operations task pool to Lua.
 * Peripheral type: "ops"
 *
 * Read-only. Use for wall-mounted Ops Dashboard and workload monitors.
 *
 * Lua example:
 *   local ops = peripheral.find("ops")
 *   for _, row in pairs(ops.getWorkloadSummary()) do
 *     print(row.division, "active:", row.active)
 *   end
 *
 *   -- Filter by division (optional arg):
 *   for _, t in pairs(ops.getTaskPool("ENGINEERING")) do
 *     print(t.name, t.status)
 *   end
 */
public class OpsPeripheral implements IPeripheral {

    private final MinecraftServer server;

    public OpsPeripheral(MinecraftServer server) {
        this.server = server;
    }

    // ── IPeripheral ───────────────────────────────────────────────────────────

    @Override
    public String getType() { return "ops"; }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof OpsPeripheral;
    }

    // ── Lua methods ───────────────────────────────────────────────────────────

    /**
     * Returns all tasks, optionally filtered by division name.
     * CC's @LuaFunction supports Optional natively — the arg is optional in Lua.
     */
    @LuaFunction
    public final Map<Integer, Object> getTaskPool(Optional<String> divisionName) throws LuaException {
        List<OpsTaskPoolEntry> pool = SecondDawnRP.TASK_SERVICE.getPoolEntries();
        if (divisionName.isPresent()) {
            Division div = parseDivision(divisionName.get());
            pool = pool.stream().filter(e -> e.getDivision() == div).toList();
        }
        return toTable(pool);
    }

    @LuaFunction
    public final int getActiveTaskCount(String divisionName) throws LuaException {
        Division div = parseDivision(divisionName);
        return (int) SecondDawnRP.TASK_SERVICE.getPoolEntries().stream()
                .filter(e -> e.getDivision() == div && e.getStatus() == OpsTaskStatus.IN_PROGRESS)
                .count();
    }

    @LuaFunction
    public final int getPendingReviewCount(String divisionName) throws LuaException {
        Division div = parseDivision(divisionName);
        return (int) SecondDawnRP.TASK_SERVICE.getPoolEntries().stream()
                .filter(e -> e.getDivision() == div && e.getStatus() == OpsTaskStatus.AWAITING_REVIEW)
                .count();
    }

    @LuaFunction
    public final Map<Integer, Object> getWorkloadSummary() {
        List<OpsTaskPoolEntry> pool = SecondDawnRP.TASK_SERVICE.getPoolEntries();
        Map<Integer, Object> table = new HashMap<>();
        int i = 1;
        for (Division div : Division.values()) {
            List<OpsTaskPoolEntry> divEntries = pool.stream()
                    .filter(e -> e.getDivision() == div).toList();
            Map<String, Object> row = new HashMap<>();
            row.put("division", div.name());
            row.put("public",   count(divEntries, OpsTaskStatus.PUBLIC));
            row.put("active",   count(divEntries, OpsTaskStatus.IN_PROGRESS));
            row.put("review",   count(divEntries, OpsTaskStatus.AWAITING_REVIEW));
            row.put("total",    divEntries.size());
            table.put(i++, row);
        }
        return table;
    }

    /** Placeholder — returns empty table until Phase 4.75 OnboardingService. */
    @LuaFunction
    public final Map<Integer, Object> getNewRecruits() {
        return new HashMap<>();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<Integer, Object> toTable(List<OpsTaskPoolEntry> entries) {
        Map<Integer, Object> table = new HashMap<>();
        int i = 1;
        for (OpsTaskPoolEntry e : entries) {
            Map<String, Object> row = new HashMap<>();
            row.put("taskId",   e.getTaskId());
            row.put("name",     e.getDisplayName());
            row.put("division", e.getDivision().name());
            row.put("status",   e.getStatus().name());
            row.put("points",   e.getRewardPoints());
            table.put(i++, row);
        }
        return table;
    }

    private int count(List<OpsTaskPoolEntry> entries, OpsTaskStatus status) {
        return (int) entries.stream().filter(e -> e.getStatus() == status).count();
    }

    private Division parseDivision(String name) throws LuaException {
        try { return Division.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new LuaException("Unknown division: " + name
                    + ". Valid: " + Arrays.toString(Division.values()));
        }
    }
}