package net.shard.seconddawnrp.tasksystem.service;

import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;

import java.util.Objects;

public class TaskRewardService {

    public void grantReward(PlayerProfile profile, TaskTemplate template) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(template, "template");

        profile.setRankPoints(profile.getRankPoints() + template.getRewardPoints());
    }
}