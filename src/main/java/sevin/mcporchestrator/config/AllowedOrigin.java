package sevin.mcporchestrator.config;

public enum AllowedOrigin {
    TB("https://tb-ca-cloud.polarisoffice.com"),
    VF("https://vf-ca-cloud.polarisoffice.com");

    private final String url;

    AllowedOrigin(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public static String[] urls() {
        AllowedOrigin[] values = values();
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].url;
        return result;
    }
}
