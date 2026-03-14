package net.shard.shipyardsrp.starfleetarchives;

import net.shard.shipyardsrp.divison.Division;
import net.shard.shipyardsrp.divison.Rank;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProfileSerializer {

    public ProfileSaveData toSaveData(PlayerProfile profile) {
        ProfileSaveData data = new ProfileSaveData();
        data.playerId = profile.getPlayerId().toString();
        data.serviceName = profile.getServiceName();
        data.division = profile.getDivision().name();
        data.progressionPath = profile.getProgressionPath().name();
        data.rank = profile.getRank().name();
        data.rankPoints = profile.getRankPoints();
        data.dutyStatus = profile.getDutyStatus().name();
        data.supervisorId = profile.getSupervisorId() == null ? null : profile.getSupervisorId().toString();

        for (Billet billet : profile.getBillets()) {
            data.billets.add(billet.name());
        }

        for (Certification certification : profile.getCertifications()) {
            data.certifications.add(certification.name());
        }

        return data;
    }

    public PlayerProfile fromSaveData(ProfileSaveData data) {
        UUID playerId = UUID.fromString(data.playerId);
        String serviceName = data.serviceName == null ? "" : data.serviceName;
        Division division = data.division == null ? Division.UNASSIGNED : Division.valueOf(data.division);
        ProgressionPath progressionPath = data.progressionPath == null
                ? ProgressionPath.ENLISTED
                : ProgressionPath.valueOf(data.progressionPath);
        Rank rank = data.rank == null ? Rank.JUNIOR_CREWMAN : Rank.valueOf(data.rank);
        int rankPoints = data.rankPoints;
        DutyStatus dutyStatus = data.dutyStatus == null ? DutyStatus.OFF_DUTY : DutyStatus.valueOf(data.dutyStatus);
        UUID supervisorId = data.supervisorId == null || data.supervisorId.isBlank()
                ? null
                : UUID.fromString(data.supervisorId);

        Set<Billet> billets = new HashSet<>();
        if (data.billets != null) {
            for (String billetName : data.billets) {
                billets.add(Billet.valueOf(billetName));
            }
        }

        Set<Certification> certifications = new HashSet<>();
        if (data.certifications != null) {
            for (String certName : data.certifications) {
                certifications.add(Certification.valueOf(certName));
            }
        }

        return new PlayerProfile(
                playerId,
                serviceName,
                division,
                progressionPath,
                rank,
                rankPoints,
                billets,
                certifications,
                dutyStatus,
                supervisorId
        );
    }
}