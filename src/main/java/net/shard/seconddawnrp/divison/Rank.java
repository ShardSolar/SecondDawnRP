package net.shard.seconddawnrp.divison;

public enum Rank {
    // Enlisted
    JUNIOR_CREWMAN("junior_crewman", RankTrack.ENLISTED, 0),
    CREWMAN("crewman", RankTrack.ENLISTED, 1),
    SENIOR_CREWMAN("senior_crewman", RankTrack.ENLISTED, 2),

    PETTY_OFFICER("petty_officer", RankTrack.ENLISTED, 3),
    SENIOR_PETTY_OFFICER("senior_petty_officer", RankTrack.ENLISTED, 4),
    CHIEF_PETTY_OFFICER("chief_petty_officer", RankTrack.ENLISTED, 5),

    // Officer / Cadet
    CADET_1("cadet_1", RankTrack.OFFICER, 0),
    CADET_2("cadet_2", RankTrack.OFFICER, 1),
    CADET_3("cadet_3", RankTrack.OFFICER, 2),
    CADET_4("cadet_4", RankTrack.OFFICER, 3),

    ENSIGN("ensign", RankTrack.OFFICER, 4),
    LIEUTENANT_JG("lieutenant_jg", RankTrack.OFFICER, 5),
    LIEUTENANT("lieutenant", RankTrack.OFFICER, 6),
    LIEUTENANT_COMMANDER("lieutenant_commander", RankTrack.OFFICER, 7),
    COMMANDER("commander", RankTrack.OFFICER, 8),
    CAPTAIN("captain", RankTrack.OFFICER, 9);

    private final String id;
    private final RankTrack track;
    private final int authorityLevel;

    Rank(String id, RankTrack track, int authorityLevel) {
        this.id = id;
        this.track = track;
        this.authorityLevel = authorityLevel;
    }

    public String getId() {
        return id;
    }

    public RankTrack getTrack() {
        return track;
    }

    public int getAuthorityLevel() {
        return authorityLevel;
    }

    public boolean isEnlisted() {
        return track == RankTrack.ENLISTED;
    }

    public boolean isOfficerTrack() {
        return track == RankTrack.OFFICER;
    }
}
