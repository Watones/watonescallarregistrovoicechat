package com.watones.minecraft.watonescallarregistrovoicechat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class MuteService {

    static final Duration FIXED_DURATION = Duration.ofDays(1);
    static final String FIXED_DURATION_TEXT = "1d";

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final String groupName;

    MuteService(JavaPlugin plugin, String groupName) {
        this.plugin = plugin;
        this.groupName = groupName;
        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (Exception ex) {
            plugin.getLogger().severe("LuckPerms no encontrado. Este plugin depende de LuckPerms.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            throw ex;
        }
    }

    CompletableFuture<ResolvedTarget> mute(String targetName) {
        return resolveTarget(targetName).thenCompose(target ->
                luckPerms.getUserManager().modifyUser(target.uuid(), user -> addMuteNode(user))
                        .thenApply(ignored -> target)
        );
    }

    CompletableFuture<ResolvedTarget> unmute(String targetName) {
        return resolveTarget(targetName).thenCompose(target ->
                luckPerms.getUserManager().modifyUser(target.uuid(), this::removeMuteNodes)
                        .thenApply(ignored -> target)
        );
    }

    private CompletableFuture<ResolvedTarget> resolveTarget(String targetName) {
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            return CompletableFuture.completedFuture(new ResolvedTarget(onlinePlayer.getUniqueId(), onlinePlayer.getName()));
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
        if (cached != null && cached.getName() != null) {
            return CompletableFuture.completedFuture(new ResolvedTarget(cached.getUniqueId(), cached.getName()));
        }

        return luckPerms.getUserManager().lookupUniqueId(targetName).thenApply(uuid -> {
            if (uuid == null) {
                throw new CompletionException(new PlayerNotFoundException(targetName));
            }
            String resolvedName = resolveKnownName(uuid, targetName);
            return new ResolvedTarget(uuid, resolvedName);
        });
    }

    private void addMuteNode(User user) {
        InheritanceNode node = InheritanceNode.builder(groupName)
                .expiry(Instant.now().plus(FIXED_DURATION))
                .build();
        user.data().add(node);
    }

    private void removeMuteNodes(User user) {
        user.data().clear(NodeType.INHERITANCE.predicate(inheritanceNode ->
                inheritanceNode.getGroupName().equalsIgnoreCase(groupName) && inheritanceNode.hasExpiry()
        ));
    }

    private String resolveKnownName(UUID uuid, String fallback) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        User loadedUser = luckPerms.getUserManager().getUser(uuid);
        if (loadedUser != null && loadedUser.getUsername() != null && !loadedUser.getUsername().isBlank()) {
            return loadedUser.getUsername();
        }

        return fallback;
    }

    record ResolvedTarget(UUID uuid, String name) {
    }
}
