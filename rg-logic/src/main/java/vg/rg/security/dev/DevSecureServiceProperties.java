package vg.rg.security.dev;

import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@ConfigurationProperties("rg.secure-service")
@Component
@ConditionalOnProperty(prefix = "rg.secure-service", name = "enabled", havingValue = "true")
public class DevSecureServiceProperties {

    private Duration authDateTtl = Duration.ofHours(1);
    private Duration allowedClockSkew = Duration.ofSeconds(30);
    private String botToken = "";

    public void setAuthDateTtl(Duration authDateTtl) { this.authDateTtl = positive(authDateTtl, "authDateTtl"); }

    public void setAllowedClockSkew(Duration allowedClockSkew) { this.allowedClockSkew = nonNegative(allowedClockSkew, "allowedClockSkew"); }

    public void setBotToken(String botToken) { this.botToken = botToken == null ? "" : botToken; }
    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static Duration positive(Duration value, String field) {
        required(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String field) {
        required(value, field);
        if (value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
