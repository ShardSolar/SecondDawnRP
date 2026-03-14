package net.shard.shipyardsrp.tasksystem.service;

import net.shard.shipyardsrp.starfleetarchives.PlayerProfile;
import net.shard.shipyardsrp.tasksystem.data.TaskTemplate;

import java.util.Objects;

public class TaskRewardService {

    public void grantReward(PlayerProfile profile, TaskTemplate template) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(template, "template");

        profile.setRankPoints(profile.getRankPoints() + template.getRewardPoints());
    }
}