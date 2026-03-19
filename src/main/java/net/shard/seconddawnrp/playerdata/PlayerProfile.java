package net.shard.seconddawnrp.playerdata;

import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.divison.Rank;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;

import java.util.*;

public class PlayerProfile {
    private final UUID playerId;
    private String serviceName;
    private Division division;
    private ProgressionPath progressionPath;
    private Rank rank;
    private int rankPoints;
    private final Set<Billet> billets;
    private final Set<Certification> certifications;
    private DutyStatus dutyStatus;
    private UUID supervisorId;

    public PlayerProfile(
            UUID playerId,
            String serviceName,
            Division division,
            ProgressionPath progressionPath,
            Rank rank,
            int rankPoints,
            Set<Billet> billets,
            Set<Certification> certifications,
            DutyStatus dutyStatus,
            UUID supervisorId
    ) {
        this.playerId = playerId;
        this.serviceName = serviceName;
        this.division = division;
        this.progressionPath = progressionPath;
        this.rank = rank;
        this.rankPoints = rankPoints;
        this.billets = new HashSet<>(billets);
        this.certifications = new HashSet<>(certifications);
        this.dutyStatus = dutyStatus;
        this.supervisorId = supervisorId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Division getDivision() {
        return division;
    }

    public void setDivision(Division division) {
        this.division = division;
    }

    public ProgressionPath getProgressionPath() {
        return progressionPath;
    }

    public void setProgressionPath(ProgressionPath progressionPath) {
        this.progressionPath = progressionPath;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public int getRankPoints() {
        return rankPoints;
    }

    public void setRankPoints(int rankPoints) {
        this.rankPoints = rankPoints;
    }

    public void addRankPoints(int amount) {
        this.rankPoints += amount;
    }

    public Set<Billet> getBillets() {
        return Collections.unmodifiableSet(billets);
    }

    public boolean hasBillet(Billet billet) {
        return billets.contains(billet);
    }

    public boolean addBillet(Billet billet) {
        return billets.add(billet);
    }

    public boolean removeBillet(Billet billet) {
        return billets.remove(billet);
    }

    public Set<Certification> getCertifications() {
        return Collections.unmodifiableSet(certifications);
    }

    public boolean hasCertification(Certification certification) {
        return certifications.contains(certification);
    }

    public boolean addCertification(Certification certification) {
        return certifications.add(certification);
    }

    public boolean removeCertification(Certification certification) {
        return certifications.remove(certification);
    }

    public DutyStatus getDutyStatus() {
        return dutyStatus;
    }

    public void setDutyStatus(DutyStatus dutyStatus) {
        this.dutyStatus = dutyStatus;
    }

    public UUID getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(UUID supervisorId) {
        this.supervisorId = supervisorId;
    }

    private List<ActiveTask> activeTasks = new ArrayList<>();
    private List<CompletedTaskRecord> completedTasks = new ArrayList<>();

    public List<ActiveTask> getActiveTasks() {
        return activeTasks;
    }

    public List<CompletedTaskRecord> getCompletedTasks() {
        return completedTasks;
    }

    public void setActiveTasks(List<ActiveTask> activeTasks) {
        this.activeTasks = activeTasks != null ? activeTasks : new ArrayList<>();
    }

    public void setCompletedTasks(List<CompletedTaskRecord> completedTasks) {
        this.completedTasks = completedTasks != null ? completedTasks : new ArrayList<>();
    }

}

