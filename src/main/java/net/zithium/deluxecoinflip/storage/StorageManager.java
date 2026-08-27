/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.storage;

import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import net.zithium.deluxecoinflip.exception.InvalidStorageHandlerException;
import net.zithium.deluxecoinflip.game.CoinflipGame;
import net.zithium.deluxecoinflip.storage.handler.StorageHandler;
import net.zithium.deluxecoinflip.storage.handler.impl.SQLiteHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager implements Listener {

    private final DeluxeCoinflipPlugin plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private StorageHandler storageHandler;

    public StorageManager(DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    public void onEnable() {
        final String configuredType = plugin.getConfig().getString("storage.type");
        if ("SQLITE".equalsIgnoreCase(configuredType)) {
            storageHandler = new SQLiteHandler();
        } else {
            throw new InvalidStorageHandlerException("Invalid storage handler specified: " + configuredType);
        }

        if (!storageHandler.onEnable(plugin)) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(player -> loadPlayerData(player.getUniqueId()));
    }

    public void onDisable(boolean shutdown) {
        if (shutdown && storageHandler != null) {
            storageHandler.onDisable();
        }
    }

    public Optional<PlayerData> getPlayer(UUID uuid) {
        return Optional.ofNullable(playerDataMap.get(uuid));
    }

    public void updateOfflinePlayerWin(UUID uuid, long profit, long beforeTax) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateWins();
            playerData.updateProfit(profit);
            playerData.updateGambled(beforeTax);
            storageHandler.savePlayer(playerData);
        });
    }

    public void updateOfflinePlayerLoss(UUID uuid, long beforeTax) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateLosses();
            playerData.updateLosses(beforeTax);
            playerData.updateGambled(beforeTax);
            storageHandler.savePlayer(playerData);
        });
    }

    public void loadPlayerData(UUID uuid) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PlayerData data = storageHandler.getPlayer(uuid);
            playerDataMap.put(uuid, data);

            processPendingRefunds(uuid);
        });
    }

    private void processPendingRefunds(UUID playerUUID) {
        try {
            List<StorageHandler.PendingRefund> pendingRefunds = storageHandler.getPendingRefunds();
            List<StorageHandler.PendingRefund> playerRefunds = pendingRefunds.stream()
                .filter(refund -> refund.playerUUID().equals(playerUUID))
                .toList();
                
            if (playerRefunds.isEmpty()) {
                return;
            }

            storageHandler.deletePendingRefund(playerUUID);
            
            for (StorageHandler.PendingRefund refund : playerRefunds) {
                EconomyProvider provider = plugin.getEconomyManager().getEconomyProvider(refund.provider());

                if (provider != null) {
                    OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUUID);
                    provider.deposit(player, refund.amount());
                    plugin.getLogger().info("Processed pending refund from server shutdown: " + refund.amount() + " " + refund.provider() + " for player " + playerUUID);
                } else {
                    plugin.getLogger().warning("Economy provider '" + refund.provider() + "' not found for pending refund to player " + playerUUID);
                    storageHandler.savePendingRefund(playerUUID, refund.provider(), refund.amount());
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to process pending refunds for player " + playerUUID + ": " + ex.getMessage());
        }
    }

    public void savePlayerData(PlayerData player, boolean removeCache) {
        UUID uuid = player.getUUID();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            storageHandler.savePlayer(player);
            if (removeCache) {
                playerDataMap.remove(uuid);
            }
        });
    }

    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }

    public StorageHandler getStorageHandler() {
        return storageHandler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        CoinflipGame game = plugin.getGameManager().getCoinflipGame(playerUUID);
        if (game != null && !game.isActiveGame()) {
            plugin.getGameManager().removeCoinflipGame(playerUUID);
        }
        
        getPlayer(playerUUID).ifPresent(data -> savePlayerData(data, true));
    }
}
