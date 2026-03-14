package net.shard.shipyardsrp.starfleetarchives;

import net.shard.shipyardsrp.divison.Division;
import net.shard.shipyardsrp.divison.Rank;

import java.util.Set;
import java.util.UUID;

public class DefaultProfileFactory {

    public PlayerProfile create(UUID playerId, String playerName) {
        return new PlayerProfile(
                playerId,
                playerName,
                Division.UNASSIGNED,
                ProgressionPath.ENLISTED,
                Rank.JUNIOR_CREWMAN,
                0,
                Set.of(),
                Set.of(),
                DutyStatus.OFF_DUTY,
                null
        );
    }
}