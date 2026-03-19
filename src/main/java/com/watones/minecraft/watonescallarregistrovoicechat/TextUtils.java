package com.watones.minecraft.watonescallarregistrovoicechat;

import org.bukkit.ChatColor;

import java.util.Map;

final class TextUtils {

    private TextUtils() {
    }

    static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    static Integer parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String value = raw.trim();
            if (value.startsWith("#")) {
                return Integer.parseInt(value.substring(1), 16);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String applyTemplate(String template, Map<String, String> placeholders) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    static String toJson(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }
}
