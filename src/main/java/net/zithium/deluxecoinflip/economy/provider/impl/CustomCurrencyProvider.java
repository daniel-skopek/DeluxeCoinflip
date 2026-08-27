/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.economy.provider.impl;

import me.clip.placeholderapi.PlaceholderAPI;
import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;

public class CustomCurrencyProvider extends EconomyProvider {

    private final DeluxeCoinflipPlugin plugin;

    private final String rawBalancePlaceholder;
    private final String withdrawCommandTemplate;
    private final String depositCommandTemplate;

    public CustomCurrencyProvider(String identifier, DeluxeCoinflipPlugin plugin) {
        super(identifier);
        this.plugin = plugin;
        this.rawBalancePlaceholder = plugin.getConfig().getString(
                "settings.providers.CUSTOM_CURRENCY.raw_balance_placeholder",
                "%vault_eco_Balance_fixed%"
        );
        this.withdrawCommandTemplate = plugin.getConfig().getString(
                "settings.providers.CUSTOM_CURRENCY.commands.withdraw",
                "eco take {player} {amount}"
        );
        this.depositCommandTemplate = plugin.getConfig().getString(
                "settings.providers.CUSTOM_CURRENCY.commands.deposit",
                "eco give {player} {amount}"
        );
    }

    @Override
    public void onEnable() {
        // Any setup needed when the provider is enabled
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        String balanceString = PlaceholderAPI.setPlaceholders(player, rawBalancePlaceholder);
        try {
            return Double.parseDouble(balanceString);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Error fetching balance for " + getSafePlayerName(player) + ": " + e.getMessage());
            return 0; // Returning zero if there's an issue with fetching placeholder. Should prevent the game from proceeding.
        }
    }

    @Override
    public void withdraw(OfflinePlayer player, double amount) {
        String formattedAmount = (amount % 1 == 0) ? String.valueOf((long) amount) : String.valueOf(amount);
        String command = withdrawCommandTemplate
                .replace("{player}", getNameOrUuid(player))
                .replace("{amount}", formattedAmount);
        executeCommand(command);
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        String formattedAmount = (amount % 1 == 0) ? String.valueOf((long) amount) : String.valueOf(amount);
        String command = depositCommandTemplate
                .replace("{player}", getNameOrUuid(player))
                .replace("{amount}", formattedAmount);
        executeCommand(command);
    }

    private void executeCommand(String command) {
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> Bukkit.dispatchCommand(console, command));
    }

    private static String getNameOrUuid(OfflinePlayer player) {
        String name = player.getName();
        return (name != null && !name.isBlank()) ? name : player.getUniqueId().toString();
    }

    private static String getSafePlayerName(OfflinePlayer player) {
        String name = player.getName();
        return (name != null && !name.isBlank()) ? name : "<Unknown:" + player.getUniqueId() + ">";
    }
}
