package net.shard.shipyardsrp.starfleetarchives;

import java.util.ArrayList;
import java.util.List;

public class ProfileSaveData {
    public String playerId;
    public String serviceName;
    public String division;
    public String progressionPath;
    public String rank;
    public int rankPoints;
    public List<String> billets = new ArrayList<>();
    public List<String> certifications = new ArrayList<>();
    public String dutyStatus;
    public String supervisorId;
}