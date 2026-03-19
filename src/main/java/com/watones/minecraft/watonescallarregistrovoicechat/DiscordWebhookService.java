package com.watones.minecraft.watonescallarregistrovoicechat;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DiscordWebhookService {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final HttpClient httpClient;
    private final ExecutorService senderPool;
    private final ConcurrentMap<String, Long> lastSentByActionAndTarget = new ConcurrentHashMap<>();
    private final URI webhookUri;

    DiscordWebhookService(JavaPlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.senderPool = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "WatonesCallar-WebhookSender");
            thread.setDaemon(true);
            return thread;
        });
        this.webhookUri = parseWebhookUri(pluginConfig.webhookUrl());
    }

    void shutdown() {
        senderPool.shutdown();
    }

    void sendAction(String staff, String target, String action, String durationText) {
        if (webhookUri == null) {
            return;
        }

        String key = action.toLowerCase(Locale.ROOT) + ":" + target.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long previous = lastSentByActionAndTarget.put(key, now);
        if (previous != null && now - previous < pluginConfig.rateLimitPerTargetMs()) {
            lastSentByActionAndTarget.put(key, previous);
            return;
        }

        pruneRateLimitEntries(now);

        senderPool.submit(() -> {
            try {
                String timestamp = Instant.now().toString();
                Map<String, String> placeholders = placeholders(action, staff, target, durationText, timestamp, pluginConfig.modalityName());
                String body = pluginConfig.discord().useEmbeds()
                        ? buildEmbedPayload(placeholders)
                        : buildContentPayload(placeholders);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(webhookUri)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    plugin.getLogger().warning("Webhook devolvio HTTP " + statusCode + ": " + trimForLog(response.body()));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Fallo al enviar webhook: " + ex.getMessage());
            }
        });
    }

    private void pruneRateLimitEntries(long now) {
        long ttl = Math.max(pluginConfig.rateLimitPerTargetMs(), 1_000L) * 10L;
        lastSentByActionAndTarget.entrySet().removeIf(entry -> now - entry.getValue() > ttl);
    }

    private String buildContentPayload(Map<String, String> placeholders) {
        StringBuilder builder = new StringBuilder("{");
        appendOptionalIdentity(builder);
        builder.append("\"content\":")
                .append(TextUtils.toJson(TextUtils.applyTemplate(pluginConfig.discord().contentTemplate(), placeholders)))
                .append("}");
        return builder.toString();
    }

    private String buildEmbedPayload(Map<String, String> placeholders) {
        PluginConfig.EmbedConfig embed = pluginConfig.discord().embed();
        StringBuilder builder = new StringBuilder("{");
        appendOptionalIdentity(builder);
        builder.append("\"embeds\":[{")
                .append("\"title\":").append(TextUtils.toJson(TextUtils.applyTemplate(embed.title(), placeholders))).append(",")
                .append("\"description\":").append(TextUtils.toJson(TextUtils.applyTemplate(embed.description(), placeholders))).append(",")
                .append("\"color\":").append(embed.color() == null ? 0xFF0044 : embed.color()).append(",")
                .append("\"timestamp\":").append(TextUtils.toJson(placeholders.get("timestamp")));

        if (!embed.fields().isEmpty()) {
            builder.append(",\"fields\":[");
            for (int i = 0; i < embed.fields().size(); i++) {
                PluginConfig.EmbedFieldConfig field = embed.fields().get(i);
                if (i > 0) {
                    builder.append(",");
                }
                builder.append("{")
                        .append("\"name\":").append(TextUtils.toJson(TextUtils.applyTemplate(field.name(), placeholders))).append(",")
                        .append("\"value\":").append(TextUtils.toJson(TextUtils.applyTemplate(field.value(), placeholders))).append(",")
                        .append("\"inline\":").append(field.inline())
                        .append("}");
            }
            builder.append("]");
        }

        builder.append(",\"footer\":{\"text\":")
                .append(TextUtils.toJson(TextUtils.applyTemplate(embed.footer(), placeholders)))
                .append("}}]}");
        return builder.toString();
    }

    private void appendOptionalIdentity(StringBuilder builder) {
        if (!isBlank(pluginConfig.discord().username())) {
            builder.append("\"username\":").append(TextUtils.toJson(pluginConfig.discord().username())).append(",");
        }
        if (!isBlank(pluginConfig.discord().avatarUrl())) {
            builder.append("\"avatar_url\":").append(TextUtils.toJson(pluginConfig.discord().avatarUrl())).append(",");
        }
    }

    private URI parseWebhookUri(String rawUrl) {
        if (isBlank(rawUrl)) {
            return null;
        }
        try {
            return URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("webhook_url invalida, se desactiva el envio a Discord.");
            return null;
        }
    }

    private static Map<String, String> placeholders(
            String action,
            String staff,
            String target,
            String duration,
            String timestamp,
            String modality
    ) {
        Map<String, String> values = new HashMap<>();
        values.put("action", action);
        values.put("staff", staff);
        values.put("target", target);
        values.put("duration", duration);
        values.put("timestamp", timestamp);
        values.put("modality", modality);
        return values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimForLog(String value) {
        if (value == null || value.isBlank()) {
            return "<sin cuerpo>";
        }
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() > 180 ? singleLine.substring(0, 180) + "..." : singleLine;
    }
}
