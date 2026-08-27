/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.game;

import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import net.zithium.deluxecoinflip.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final DeluxeCoinflipPlugin plugin;
    private final Map<UUID, CoinflipGame> coinflipGames;
    private final StorageManager storageManager;

    public GameManager(DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;
        this.coinflipGames = new HashMap<>();
        this.storageManager = plugin.getStorageManager();
    }

    /**
     * Add a coinflip game
     *
     * @param uuid The UUID of the player creating the game
     * @param game The coinflip game object
     */
    public void addCoinflipGame(UUID uuid, CoinflipGame game) {
        coinflipGames.put(uuid, game);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> storageManager.getStorageHandler().saveCoinflip(game));
    }

    /**
     * Delete an existing coinflip game
     *
     * <p>Scheduling on Folia when the plugin is disabling does not
     * work and shoots an exception. Please refrain from modifying
     * this logic unless you know what you're doing.</p>
     *
     * @param uuid The UUID of the player removing the game
     */
    public void removeCoinflipGame(@NotNull UUID uuid) {
        removeCoinflipGame(uuid, true);
    }

    /**
     * Delete an existing coinflip game with option to control refunding
     *
     * @param uuid The UUID of the player removing the game
     * @param shouldRefund Whether to refund the player (false when game is being joined)
     */
    public void removeCoinflipGame(@NotNull UUID uuid, boolean shouldRefund) {
        CoinflipGame game = coinflipGames.remove(uuid);

        if (!plugin.isEnabled()) {
            try {
                storageManager.getStorageHandler().deleteCoinflip(uuid);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to delete coinflip for " + uuid + " during shutdown: " + ex.getMessage());
            }

            return;
        }

        if (game != null && !game.isActiveGame() && shouldRefund) {
            refundPlayer(game);
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                storageManager.getStorageHandler().deleteCoinflip(uuid);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to delete coinflip for " + uuid + ": " + ex.getMessage());
            }
        });
    }

    private void refundPlayer(CoinflipGame game) {
        try {
            EconomyProvider provider = plugin.getEconomyManager().getEconomyProvider(game.getProvider());
            if (provider == null) {
                plugin.getLogger().warning("Economy provider '" + game.getProvider() + "' not found for refund to player " + game.getPlayerUUID());
                return;
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(game.getPlayerUUID());
            provider.deposit(player, game.getAmount());
            
            plugin.getLogger().info("Refunded " + game.getAmount() + " " + game.getProvider() + " to player " + game.getPlayerUUID() + " for cancelled game");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to refund player " + game.getPlayerUUID() + " for cancelled game: " + ex.getMessage());
            storageManager.getStorageHandler().savePendingRefund(game.getPlayerUUID(), game.getProvider(), game.getAmount());
        }
    }

    public Map<UUID, CoinflipGame> getCoinflipGames() {
        return coinflipGames;
    }

    public CoinflipGame getCoinflipGame(@NotNull UUID playerUUID) {
        return coinflipGames.get(playerUUID);
    }
}
