package br.com.xklpack.economy.command;

import br.com.xklpack.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class TopCommand implements CommandExecutor {
    private final EconomyService economy;

    public TopCommand(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xklpack.command.top")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }

        boolean onlineOnly = args.length > 0 && args[0].equalsIgnoreCase("online");
        int currencyIndex = onlineOnly ? 1 : 0;
        String currency = args.length > currencyIndex
                ? args[currencyIndex].toLowerCase(Locale.ROOT) : "coins";
        if (!economy.isCurrencyEnabled(currency)) {
            sender.sendMessage("§cMoeda inválida. Disponíveis: " + String.join(", ", economy.getCurrencies()));
            return true;
        }

        Set<UUID> online = onlineOnly
                ? Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet())
                : null;
        try {
            var entries = economy.getTop(currency, 10, online);
            sender.sendMessage("§6§lRanking " + (onlineOnly ? "online" : "geral") + " — "
                    + economy.getCurrencyName(currency));
            if (entries.isEmpty()) {
                sender.sendMessage("§7Ainda não há saldos registrados nessa moeda.");
                return true;
            }
            int position = 1;
            for (EconomyService.RankingEntry entry : entries) {
                sender.sendMessage("§e#" + position++ + " §f" + entry.playerName()
                        + " §7- §a" + economy.format(currency, entry.balance()));
            }
        } catch (SQLException exception) {
            sender.sendMessage("§cNão foi possível carregar o ranking.");
            exception.printStackTrace();
        }
        return true;
    }
}
