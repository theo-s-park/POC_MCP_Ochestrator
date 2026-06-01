package sevin.mcporchestrator.infra.domain;

public enum AwsRegion {
    SEOUL("ap-northeast-2"),
    US_WEST_1("us-west-1");

    private final String id;

    AwsRegion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public software.amazon.awssdk.regions.Region toSdkRegion() {
        return software.amazon.awssdk.regions.Region.of(id);
    }
}
