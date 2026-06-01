package sevin.mcporchestrator.infra.domain;

public enum LambdaProvisionStep {
    STARTED("Lambda 함수 생성 요청 중..."),
    CREATING_FUNCTION("Lambda 함수 생성 중..."),
    ACTIVATING_URL("Function URL 활성화 중..."),
    COMPLETE("완료"),
    FAILED("실패");

    private final String label;

    LambdaProvisionStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
