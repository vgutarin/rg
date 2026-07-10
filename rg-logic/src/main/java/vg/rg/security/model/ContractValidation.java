package vg.rg.security.model;

final class ContractValidation {

    private ContractValidation() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static String bounded(String value, String field, int min, int max) {
        required(value, field);
        if (value.length() < min || value.length() > max) {
            throw new IllegalArgumentException(field + " is outside accepted bounds");
        }
        return value;
    }
}
