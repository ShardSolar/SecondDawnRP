package net.shard.shipyardsrp.starfleetarchives;

public enum Certification {
    PHASER_QUALIFIED("phaser", CertificationCategory.SECURITY),
    HELM_CERTIFIED("helm", CertificationCategory.COMMAND),
    TRICORDER_BASIC("tricorder_basic", CertificationCategory.SCIENCE),
    MEDICAL_BASIC("medical_basic", CertificationCategory.MEDICAL),
    ENGINEERING_BASIC("engineering_basic", CertificationCategory.ENGINEERING),
    OPERATIONS_DISPATCH("operations_dispatch", CertificationCategory.OPERATIONS);

    private final String id;
    private final CertificationCategory category;

    Certification(String id, CertificationCategory category) {
        this.id = id;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public CertificationCategory getCategory() {
        return category;
    }
}