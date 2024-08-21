package utils.config;

public class SecretsLoader {
    public String getSecret(String secretName) {
        return System.getenv(secretName);
    }
}
