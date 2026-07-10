package vg.rg.security.dev;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vg.rg.security.model.TelegramInitDataRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public final class TelegramInitDataVerifier {

    public enum Status { VALID, INVALID, EXPIRED }

    public record Result(Status status, OptionalLong telegramUserId) {
        static Result valid(long userId) { return new Result(Status.VALID, OptionalLong.of(userId)); }
        static Result invalid() { return new Result(Status.INVALID, OptionalLong.empty()); }
        static Result expired() { return new Result(Status.EXPIRED, OptionalLong.empty()); }
    }

    private static final int MAX_JSON_DEPTH = 16;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String botToken;
    private final Duration authDateTtl;
    private final Duration allowedClockSkew;

    public TelegramInitDataVerifier(ObjectMapper objectMapper, Clock clock, String botToken,
                                    Duration authDateTtl, Duration allowedClockSkew) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
        this.clock = java.util.Objects.requireNonNull(clock);
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Telegram bot token is required");
        }
        this.botToken = botToken;
        this.authDateTtl = positive(authDateTtl, "authDateTtl");
        this.allowedClockSkew = nonNegative(allowedClockSkew, "allowedClockSkew");
    }

    public Result verify(TelegramInitDataRequest request) {
        try {
            var parameters = parseQuery(request.initData());
            var dateStatus = validateAuthDate(parameters.get("auth_date"));
            if (dateStatus != Status.VALID) {
                return new Result(dateStatus, OptionalLong.empty());
            }
            if (!validHash(parameters)) {
                return Result.invalid();
            }
            var user = parseUser(parameters.get("user"));
            return user.isPresent() ? Result.valid(user.getAsLong()) : Result.invalid();
        } catch (RuntimeException exception) {
            return Result.invalid();
        }
    }

    private Map<String, String> parseQuery(String initData) {
        var parameters = new LinkedHashMap<String, String>();
        for (var parameter : initData.split("&", -1)) {
            var parts = parameter.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed init data");
            }
            var key = decode(parts[0]);
            var value = decode(parts[1]);
            if (key.isBlank() || parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate or empty parameter");
            }
        }
        return parameters;
    }

    private Status validateAuthDate(String value) {
        if (value == null || value.isBlank()) {
            return Status.INVALID;
        }
        try {
            var authDate = Instant.ofEpochSecond(Long.parseLong(value));
            var now = clock.instant();
            if (authDate.isAfter(now.plus(allowedClockSkew))) {
                return Status.INVALID;
            }
            if (authDate.isBefore(now.minus(authDateTtl))) {
                return Status.EXPIRED;
            }
            return Status.VALID;
        } catch (RuntimeException exception) {
            return Status.INVALID;
        }
    }

    private boolean validHash(Map<String, String> parameters) {
        var providedHash = parameters.get("hash");
        if (providedHash == null || providedHash.length() != 64) {
            return false;
        }
        var dataCheckString = parameters.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        try {
            var expected = hmacSha256(
                    hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8)),
                    dataCheckString.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(providedHash));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private OptionalLong parseUser(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            var user = objectMapper.readTree(userJson);
            if (depth(user, 0) > MAX_JSON_DEPTH || !user.isObject()) {
                return OptionalLong.empty();
            }
            var bot = user.get("is_bot");
            if (bot != null && bot.asBoolean(false)) {
                return OptionalLong.empty();
            }
            JsonNode id = user.get("id");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(id.longValue());
        } catch (RuntimeException exception) {
            return OptionalLong.empty();
        }
    }

    private int depth(JsonNode node, int current) {
        if (current > MAX_JSON_DEPTH || node == null || node.isValueNode()) {
            return current;
        }
        var maximum = current;
        for (var child : node) {
            maximum = Math.max(maximum, depth(child, current + 1));
            if (maximum > MAX_JSON_DEPTH) {
                return maximum;
            }
        }
        return maximum;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot verify Telegram init data", exception);
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Duration nonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
