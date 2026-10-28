package me.timeditem.item;

public enum ExpireAction {
    DELETE,
    SEAL;

    public static ExpireAction fromString(String value) {
        return fromString(value, null);
    }

    public static ExpireAction fromString(String value, ExpireAction fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        String upper = value.trim().toUpperCase();
        return switch (upper) {
            case "SEAL", "SEALED", "LOCK", "PHONG_AN" -> SEAL;
            case "DELETE", "REMOVE", "XOA" -> DELETE;
            default -> fallback;
        };
    }

    public static ExpireAction fromStringOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        return switch (upper) {
            case "SEAL", "SEALED" -> SEAL;
            case "DELETE", "REMOVE" -> DELETE;
            default -> null;
        };
    }
}
