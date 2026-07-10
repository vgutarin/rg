package vg.rg.security.dev;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vg.rg.security.model.TelegramInitDataRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramInitDataVerifierTest {

    private static final String TOKEN = "synthetic-test-bot-token";
    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private final TelegramInitDataVerifier verifier = new TelegramInitDataVerifier(
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), TOKEN,
            Duration.ofHours(1), Duration.ofSeconds(30));

    @Test
    void verify_signedCurrentNonBotUser_returnsValidUser() {
        var result = verifier.verify(request(signed(user("123456", false), NOW.minusSeconds(60))));

        assertThat(result.status()).isEqualTo(TelegramInitDataVerifier.Status.VALID);
        assertThat(result.telegramUserId()).hasValue(123456L);
    }

    @Test
    void verify_duplicateKey_returnsInvalid() {
        var valid = signed(user("123456", false), NOW);
        assertThat(verifier.verify(request(valid + "&auth_date=1")).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_invalidPercentEncoding_returnsInvalid() {
        assertThat(verifier.verify(request("user=%ZZ&auth_date=1&hash=" + "0".repeat(64))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_badSignature_returnsInvalid() {
        var valid = signed(user("123456", false), NOW);
        assertThat(verifier.verify(request(valid.replaceFirst("hash=[0-9a-f]+", "hash=" + "0".repeat(64)))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_expiredAuthDate_returnsExpired() {
        assertThat(verifier.verify(request(signed(user("1", false), NOW.minusSeconds(3601)))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.EXPIRED);
    }

    @Test
    void verify_excessivelyFutureAuthDate_returnsInvalid() {
        assertThat(verifier.verify(request(signed(user("1", false), NOW.plusSeconds(31)))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_botUser_returnsInvalid() {
        assertThat(verifier.verify(request(signed(user("1", true), NOW))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_missingUser_returnsInvalid() {
        assertThat(verifier.verify(request(signed(null, NOW))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_nonIntegralUserId_returnsInvalid() {
        assertThat(verifier.verify(request(signed("{\"id\":1.5,\"is_bot\":false}", NOW))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    @Test
    void verify_excessiveJsonDepth_returnsInvalid() {
        var nested = "{\"id\":1,\"x\":" + "[".repeat(18) + "0" + "]".repeat(18) + "}";
        assertThat(verifier.verify(request(signed(nested, NOW))).status())
                .isEqualTo(TelegramInitDataVerifier.Status.INVALID);
    }

    private TelegramInitDataRequest request(String initData) {
        return new TelegramInitDataRequest(initData);
    }

    private String signed(String user, Instant authDate) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("query_id", "synthetic-query");
        if (user != null) {
            parameters.put("user", user);
        }
        parameters.put("auth_date", Long.toString(authDate.getEpochSecond()));
        var signature = sign(parameters);
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&")) + "&hash=" + signature;
    }

    private String sign(Map<String, String> parameters) {
        var check = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(hmac(
                hmac("WebAppData".getBytes(StandardCharsets.UTF_8), TOKEN.getBytes(StandardCharsets.UTF_8)),
                check.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String user(String id, boolean bot) {
        return "{\"id\":" + id + ",\"is_bot\":" + bot + ",\"first_name\":\"Synthetic\"}";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
