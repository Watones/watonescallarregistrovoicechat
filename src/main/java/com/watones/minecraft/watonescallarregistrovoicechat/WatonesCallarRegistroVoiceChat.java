package com.watones.minecraft.watonescallarregistrovoicechat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class WatonesCallarRegistroVoiceChat extends JavaPlugin {

    private PluginConfig pluginConfig;
    private MuteService muteService;
    private DiscordWebhookService webhookService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pluginConfig = PluginConfig.load(this);
        this.muteService = new MuteService(this, pluginConfig.groupName());
        this.webhookService = new DiscordWebhookService(this, pluginConfig);

        getLogger().info("WatonesCallarRegistroVoiceChat habilitado.");
    }

    @Override
    public void onDisable() {
        if (webhookService != null) {
            webhookService.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if ("callar".equals(name)) {
            if (!sender.hasPermission("watonescallarregistrovoicechat.srmod")) {
                sender.sendMessage(pluginConfig.prefixed(pluginConfig.messages().noPerm()));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(pluginConfig.prefixed(pluginConfig.messages().usageCallar()));
                return true;
            }
            muteTarget(sender, args[0]);
            return true;
        }

        if ("descallar".equals(name)) {
            if (!sender.hasPermission("watonescallarregistrovoicechat.srmod")) {
                sender.sendMessage(pluginConfig.prefixed(pluginConfig.messages().noPerm()));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(pluginConfig.prefixed(pluginConfig.messages().usageDescallar()));
                return true;
            }
            unmuteTarget(sender, args[0]);
            return true;
        }

        return false;
    }

    private void muteTarget(CommandSender staff, String targetName) {
        muteService.mute(targetName).thenAccept(target ->
                Bukkit.getScheduler().runTask(this, () -> {
                    staff.sendMessage(pluginConfig.prefixed(pluginConfig.messages()
                            .muted()
                            .replace("%target%", target.name())));
                    webhookService.sendAction(actorName(staff), target.name(), "MUTE", MuteService.FIXED_DURATION_TEXT);
                })
        ).exceptionally(ex -> {
            handleFailure(staff, targetName, ex);
            return null;
        });
    }

    private void unmuteTarget(CommandSender staff, String targetName) {
        muteService.unmute(targetName).thenAccept(target ->
                Bukkit.getScheduler().runTask(this, () -> {
                    staff.sendMessage(pluginConfig.prefixed(pluginConfig.messages()
                            .unmuted()
                            .replace("%target%", target.name())));
                    webhookService.sendAction(actorName(staff), target.name(), "UNMUTE", MuteService.FIXED_DURATION_TEXT);
                })
        ).exceptionally(ex -> {
            handleFailure(staff, targetName, ex);
            return null;
        });
    }

    private void handleFailure(CommandSender staff, String targetName, Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        if (cause instanceof PlayerNotFoundException) {
            Bukkit.getScheduler().runTask(this, () -> staff.sendMessage(pluginConfig.prefixed(
                    pluginConfig.messages().notFound().replace("%target%", targetName)
            )));
            return;
        }

        getLogger().warning("Error procesando accion sobre " + targetName + ": " + cause.getMessage());
        Bukkit.getScheduler().runTask(this, () ->
                staff.sendMessage(pluginConfig.prefixed(pluginConfig.messages().error()))
        );
    }

    private static String actorName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "CONSOLE";
    }
}
