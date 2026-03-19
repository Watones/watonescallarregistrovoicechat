package com.watones.minecraft.watonescallarregistrovoicechat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record PluginConfig(
        String groupName,
        String webhookUrl,
        String modalityName,
        long rateLimitPerTargetMs,
        MessageConfig messages,
        DiscordConfig discord
) {

    static PluginConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        List<EmbedFieldConfig> embedFields = new ArrayList<>();
        if (config.isList("discord.embed.fields")) {
            for (Object value : config.getList("discord.embed.fields")) {
                if (value instanceof Map<?, ?> map) {
                    embedFields.add(new EmbedFieldConfig(
                            Objects.toString(map.get("name"), ""),
                            Objects.toString(map.get("value"), ""),
                            Boolean.parseBoolean(Objects.toString(map.get("inline"), "false"))
                    ));
                }
            }
        }

        return new PluginConfig(
                config.getString("group_name", "mutevoice"),
                config.getString("webhook_url", ""),
                config.getString("modality_name", "Survival"),
                config.getLong("rate_limit_ms_per_target", 1500L),
                new MessageConfig(
                        TextUtils.color(config.getString("messages.prefix", "&4&lWATONES &8| &r")),
                        TextUtils.color(config.getString("messages.usage_callar", "&cUso: &7/callar <jugador>")),
                        TextUtils.color(config.getString("messages.usage_descallar", "&cUso: &7/descallar <jugador>")),
                        TextUtils.color(config.getString("messages.muted", "&aJugador &f%target% &amuteado en voice chat por &e1 dia&a.")),
                        TextUtils.color(config.getString("messages.unmuted", "&aMute de voice chat retirado de &f%target%&a.")),
                        TextUtils.color(config.getString("messages.no_perm", "&cNo tienes permiso.")),
                        TextUtils.color(config.getString("messages.not_found", "&cNo encontre al jugador &f%target%&c.")),
                        TextUtils.color(config.getString("messages.error", "&cOcurrio un error, intenta nuevamente."))
                ),
                new DiscordConfig(
                        config.getBoolean("discord.use_embeds", true),
                        config.getString("discord.username", ""),
                        config.getString("discord.avatar_url", ""),
                        config.getString(
                                "discord.content_template",
                                "**[{action}]** Staff: {staff} -> Jugador: {target} (duracion: {duration}) | {modality} | {timestamp}"
                        ),
                        new EmbedConfig(
                                config.getString("discord.embed.title", "Voice Chat {action}"),
                                config.getString("discord.embed.description", "Registro de moderacion | Modalidad: {modality}"),
                                config.getString("discord.embed.footer", "Watones | {timestamp}"),
                                TextUtils.parseColor(config.getString("discord.embed.color", "#FF0044")),
                                embedFields
                        )
                )
        );
    }

    String prefixed(String message) {
        return messages.prefix() + message;
    }

    record MessageConfig(
            String prefix,
            String usageCallar,
            String usageDescallar,
            String muted,
            String unmuted,
            String noPerm,
            String notFound,
            String error
    ) {
    }

    record DiscordConfig(
            boolean useEmbeds,
            String username,
            String avatarUrl,
            String contentTemplate,
            EmbedConfig embed
    ) {
    }

    record EmbedConfig(
            String title,
            String description,
            String footer,
            Integer color,
            List<EmbedFieldConfig> fields
    ) {
    }

    record EmbedFieldConfig(String name, String value, boolean inline) {
    }
}
