package com.watones.minecraft.watonescallarregistrovoicechat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class WatonesCallarRegistroVoiceChat extends JavaPlugin {

    private static final Duration FIXED_DURATION = Duration.ofDays(1); // 1 día fijo
    private static final String FIXED_DURATION_TEXT = "1d";

    private LuckPerms lp;
    private String groupName;
    private String webhookUrl;
    private String modalityName;

    private String msgPrefix, msgUsageCallar, msgUsageDescallar, msgMuted, msgUnmuted, msgNoPerm, msgNotFound, msgError;

    private long rateLimitPerTargetMs;
    private boolean useEmbeds;
    private String usernameOverride, avatarUrl, contentTemplate;

    private String embedTitle, embedDescription, embedFooter;
    private Integer embedColor;
    private List<EmbedField> embedFields;

    private HttpClient http;
    private ExecutorService senderPool;
    private final Map<String, Long> lastSentByTarget = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.groupName = getConfig().getString("group_name", "mutevoice");
        this.webhookUrl = getConfig().getString("webhook_url", "");
        this.modalityName = getConfig().getString("modality_name", "Survival");

        // mensajes
        this.msgPrefix = color(getConfig().getString("messages.prefix", "&4&lWATONES &8| &r"));
        this.msgUsageCallar = color(getConfig().getString("messages.usage_callar", "&cUso: &7/callar <jugador>"));
        this.msgUsageDescallar = color(getConfig().getString("messages.usage_descallar", "&cUso: &7/descallar <jugador>"));
        this.msgMuted = color(getConfig().getString("messages.muted", "&a✔ Jugador &f%target% &amuteado en voice chat por &e1 día&a."));
        this.msgUnmuted = color(getConfig().getString("messages.unmuted", "&a✔ Mute de voice chat retirado de &f%target%&a."));
        this.msgNoPerm = color(getConfig().getString("messages.no_perm", "&cNo tienes permiso."));
        this.msgNotFound = color(getConfig().getString("messages.not_found", "&cNo encontré al jugador &f%target%&c."));
        this.msgError = color(getConfig().getString("messages.error", "&cOcurrió un error, intenta nuevamente."));

        // discord
        this.rateLimitPerTargetMs = getConfig().getLong("rate_limit_ms_per_target", 1500L);
        this.useEmbeds = getConfig().getBoolean("discord.use_embeds", true);
        this.usernameOverride = getConfig().getString("discord.username", "");
        this.avatarUrl = getConfig().getString("discord.avatar_url", "");
        this.contentTemplate = getConfig().getString(
                "discord.content_template",
                "**[{action}]** Staff: {staff} → Jugador: {target} (duración: {duration}) • {modality} • {timestamp}"
        );
        this.embedTitle = getConfig().getString("discord.embed.title", "Voice Chat {action}");
        this.embedDescription = getConfig().getString("discord.embed.description", "Registro de moderación • Modalidad: {modality}");
        this.embedFooter = getConfig().getString("discord.embed.footer", "Watones • {timestamp}");
        this.embedColor = parseColor(getConfig().getString("discord.embed.color", "#FF0044"));
        this.embedFields = new ArrayList<>();
        if (getConfig().isList("discord.embed.fields")) {
            for (Object o : getConfig().getList("discord.embed.fields")) {
                if (o instanceof Map<?, ?> m) {
                    String name = Objects.toString(m.get("name"), "");
                    String value = Objects.toString(m.get("value"), "");
                    boolean inline = Boolean.parseBoolean(Objects.toString(m.get("inline"), "false"));
                    embedFields.add(new EmbedField(name, value, inline));
                }
            }
        }

        try {
            this.lp = LuckPermsProvider.get();
        } catch (Exception e) {
            getLogger().severe("LuckPerms no encontrado. Este plugin depende de LuckPerms.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.http = HttpClient.newHttpClient();
        this.senderPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WatonesCallar-WebhookSender");
            t.setDaemon(true);
            return t;
        });

        getLogger().info("WatonesCallarRegistroVoiceChat habilitado.");
    }

    @Override
    public void onDisable() {
        if (senderPool != null) senderPool.shutdown();
    }

    // -------------------- Commands --------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("callar")) {
            if (!sender.hasPermission("watonescallarregistrovoicechat.srmod")) {
                sender.sendMessage(msgPrefix + msgNoPerm);
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(msgPrefix + msgUsageCallar);
                return true;
            }
            muteUser(sender, args[0]); // siempre 1 día fijo
            return true;
        }

        if (name.equals("descallar")) {
            if (!sender.hasPermission("watonescallarregistrovoicechat.srmod")) {
                sender.sendMessage(msgPrefix + msgNoPerm);
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(msgPrefix + msgUsageDescallar);
                return true;
            }
            unmuteUser(sender, args[0]);
            return true;
        }

        return false;
    }

    private void muteUser(CommandSender staff, String targetName) {
        final OfflinePlayer off = getOfflineByName(targetName);
        if (off == null) {
            staff.sendMessage(msgPrefix + msgNotFound.replace("%target%", targetName));
            return;
        }
        final UUID uuid = off.getUniqueId();

        lp.getUserManager().modifyUser(uuid, (User user) -> {
            InheritanceNode node = InheritanceNode.builder(groupName)
                    .expiry(Instant.now().plus(FIXED_DURATION))
                    .build();
            user.data().add(node);
        }).thenRun(() -> {
            Bukkit.getScheduler().runTask(this, () ->
                    staff.sendMessage(msgPrefix + msgMuted.replace("%target%", orName(off, targetName)))
            );
            String actor = (staff instanceof Player p) ? p.getName() : "CONSOLE";
            sendWebhook(actor, orName(off, targetName), "MUTE", FIXED_DURATION_TEXT);
        }).exceptionally(ex -> {
            getLogger().warning("Error aplicando mute a " + targetName + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(this, () -> staff.sendMessage(msgPrefix + msgError));
            return null;
        });
    }

    private void unmuteUser(CommandSender staff, String targetName) {
        final OfflinePlayer off = getOfflineByName(targetName);
        if (off == null) {
            staff.sendMessage(msgPrefix + msgNotFound.replace("%target%", targetName));
            return;
        }
        final UUID uuid = off.getUniqueId();

        lp.getUserManager().modifyUser(uuid, (User user) -> {
            user.data().clear(NodeType.INHERITANCE.predicate(inh -> inh.getGroupName().equalsIgnoreCase(groupName)));
        }).thenRun(() -> {
            Bukkit.getScheduler().runTask(this, () ->
                    staff.sendMessage(msgPrefix + msgUnmuted.replace("%target%", orName(off, targetName)))
            );
            String actor = (staff instanceof Player p) ? p.getName() : "CONSOLE";
            sendWebhook(actor, orName(off, targetName), "UNMUTE", FIXED_DURATION_TEXT);
        }).exceptionally(ex -> {
            getLogger().warning("Error quitando mute a " + targetName + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(this, () -> staff.sendMessage(msgPrefix + msgError));
            return null;
        });
    }

    // -------------------- Discord --------------------

    private void sendWebhook(String staff, String target, String action, String durationText) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        long now = System.currentTimeMillis();
        Long last = lastSentByTarget.getOrDefault(target.toLowerCase(Locale.ROOT), 0L);
        if (now - last < rateLimitPerTargetMs) return;
        lastSentByTarget.put(target.toLowerCase(Locale.ROOT), now);

        senderPool.submit(() -> {
            try {
                String timestamp = Instant.now().toString();
                Map<String, String> ph = placeholders(action, staff, target, durationText, timestamp, modalityName);

                String json;
                if (useEmbeds) {
                    String embedJson = buildEmbedJson(ph);
                    String base = "{";
                    if (!isBlank(usernameOverride)) base += "\"username\":" + toJson(usernameOverride) + ",";
                    if (!isBlank(avatarUrl)) base += "\"avatar_url\":" + toJson(avatarUrl) + ",";
                    base += "\"embeds\":[" + embedJson + "]}";
                    json = base;
                } else {
                    String content = applyTemplate(contentTemplate, ph);
                    String base = "{";
                    if (!isBlank(usernameOverride)) base += "\"username\":" + toJson(usernameOverride) + ",";
                    if (!isBlank(avatarUrl)) base += "\"avatar_url\":" + toJson(avatarUrl) + ",";
                    base += "\"content\":" + toJson(content) + "}";
                    json = base;
                }

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                getLogger().warning("Fallo al enviar webhook: " + e.getMessage());
            }
        });
    }

    private String buildEmbedJson(Map<String, String> ph) {
        String title = applyTemplate(embedTitle, ph);
        String desc = applyTemplate(embedDescription, ph);
        String footerText = applyTemplate(embedFooter, ph);
        int colorValue = (embedColor != null ? embedColor : 0xFF0044);

        StringBuilder fieldsJson = new StringBuilder();
        if (embedFields != null && !embedFields.isEmpty()) {
            for (int i = 0; i < embedFields.size(); i++) {
                EmbedField f = embedFields.get(i);
                if (i > 0) fieldsJson.append(",");
                fieldsJson.append("{")
                        .append("\"name\":").append(toJson(applyTemplate(f.name(), ph))).append(",")
                        .append("\"value\":").append(toJson(applyTemplate(f.value(), ph))).append(",")
                        .append("\"inline\":").append(f.inline())
                        .append("}");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{")
                .append("\"title\":").append(toJson(title)).append(",")
                .append("\"description\":").append(toJson(desc)).append(",")
                .append("\"color\":").append(colorValue).append(",")
                .append("\"timestamp\":").append(toJson(ph.getOrDefault("timestamp",""))).append(",");
        if (fieldsJson.length() > 0) sb.append("\"fields\":[").append(fieldsJson).append("],");
        sb.append("\"footer\":{\"text\":").append(toJson(footerText)).append("}")
                .append("}");
        return sb.toString();
    }

    // -------------------- Utils --------------------

    private OfflinePlayer getOfflineByName(String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        return Bukkit.getOfflinePlayer(name);
    }

    private static String orName(OfflinePlayer off, String fallback) {
        return off.getName() == null ? fallback : off.getName();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Integer parseColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String s = raw.trim();
            if (s.startsWith("#")) return Integer.parseInt(s.substring(1), 16);
            return Integer.parseInt(s);
        } catch (Exception ignored) { return null; }
    }

    private static Map<String, String> placeholders(String action, String staff, String target, String duration, String timestamp, String modality) {
        Map<String, String> m = new HashMap<>();
        m.put("action", action);
        m.put("staff", staff);
        m.put("target", target);
        m.put("duration", duration);
        m.put("timestamp", timestamp);
        m.put("modality", modality);
        return m;
    }

    private static String applyTemplate(String template, Map<String, String> ph) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static String toJson(String s) {
        if (s == null) return "\"\"";
        String t = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "\"" + t + "\"";
    }

    private record EmbedField(String name, String value, boolean inline) {}
}
