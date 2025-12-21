/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.storage.handler.impl;

import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.cache.ActiveGamesCache;
import net.zithium.deluxecoinflip.config.Messages;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import net.zithium.deluxecoinflip.game.CoinflipGame;
import net.zithium.deluxecoinflip.storage.handler.GameShutdownProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record DefaultGameShutdownProvider(DeluxeCoinflipPlugin plugin) implements GameShutdownProvider {

    @Override
    public void shutdownAll() {
        shutdownActiveGames();
        shutdownNonActiveListings();
    }

    @Override
    public void shutdownActiveGames() {
        final ActiveGamesCache activeCache = plugin.getActiveGamesCache();

        final Collection<CoinflipGame> activeGames = new LinkedHashSet<>(activeCache.getAllUniqueGames());
        for (CoinflipGame game : activeGames) {
            if (game == null) {
                continue;
            }

            if (game.isActiveGame()) {
                game.stopAnimation();
            }

            final Set<UUID> participants = collectParticipants(activeCache, game);
            activeCache.unregister(game);

            final long amount = game.getAmount();

            for (UUID participantId : participants) {
                saveRefundForLater(participantId, amount, game.getProvider());
            }

            removeListingAndStorage(game.getPlayerUUID());
        }

        activeCache.clear();
    }

    @Override
    public void shutdownNonActiveListings() {

        final Map<UUID, CoinflipGame> listings = plugin.getGameManager().getCoinflipGames();
        final List<CoinflipGame> snapshot = new ArrayList<>(listings.values());

        for (CoinflipGame game : snapshot) {
            if (game == null || game.isActiveGame()) {
                continue;
            }

            final UUID creatorId = game.getPlayerUUID();
            if (creatorId == null) {
                continue;
            }

            final long amount = game.getAmount();

            saveRefundForLater(creatorId, amount, game.getProvider());
            removeListingAndStorage(creatorId);
        }
    }

    private Set<UUID> collectParticipants(ActiveGamesCache cache, CoinflipGame game) {
        final Set<UUID> participants = new LinkedHashSet<>(cache.getParticipants(game));

        final UUID creatorId = game.getPlayerUUID();
        final UUID opponentId = game.getOpponentUUID();

        if (creatorId != null) {
            participants.add(creatorId);
        }
        if (opponentId != null) {
            participants.add(opponentId);
        }

        return participants;
    }

    private void saveRefundForLater(UUID playerUUID, long amount, String providerIdentifier) {
        if (playerUUID == null) {
            return;
        }

        plugin.getStorageManager().getStorageHandler().savePendingRefund(playerUUID, providerIdentifier, amount);
        plugin.getLogger().info("Saved pending refund: " + amount + " " + providerIdentifier + " for player " + playerUUID);
    }

    private void removeListingAndStorage(UUID creatorUUID) {
        if (creatorUUID == null) {
            return;
        }

        plugin.getGameManager().removeCoinflipGame(creatorUUID);
        plugin.getStorageManager().getStorageHandler().deleteCoinflip(creatorUUID);
    }
}
