package vg.rg.security.support;

import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.TelegramInitDataRequest;
import vg.unique.id.model.UniqueId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SecureAuthorizationFixtures {

    public static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String SYNTHETIC_BOT_TOKEN = "test-only-bot-token";

    private SecureAuthorizationFixtures() {
    }

    public static TelegramInitDataRequest request(String initData) {
        return new TelegramInitDataRequest(initData);
    }

    public static AuthenticatedUserPrincipal principal(String... permissions) {
        return establishedPrincipal(Set.of(permissions));
    }

    public static AuthenticatedUserPrincipal establishedPrincipal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                new UniqueId(1L),
                "Test User",
                permissions,
                true,
                AuthenticationFlow.TELEGRAM);
    }

    public static AuthenticatedUserPrincipal provisionalPrincipal(String... permissions) {
        return new AuthenticatedUserPrincipal(
                null,
                null,
                Set.of(permissions),
                false,
                AuthenticationFlow.TELEGRAM);
    }

    public static Set<String> syntheticPermissions(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "resource" + index + ":view")
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String signedInitData(long telegramUserId, Instant authDate) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("query_id", "synthetic-query");
        parameters.put("user", "{\"id\":" + telegramUserId + ",\"is_bot\":false}");
        parameters.put("auth_date", Long.toString(authDate.getEpochSecond()));
        var dataCheckString = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        var signature = HexFormat.of().formatHex(hmac(
                hmac("WebAppData".getBytes(StandardCharsets.UTF_8), SYNTHETIC_BOT_TOKEN.getBytes(StandardCharsets.UTF_8)),
                dataCheckString.getBytes(StandardCharsets.UTF_8)));
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&")) + "&hash=" + signature;
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
