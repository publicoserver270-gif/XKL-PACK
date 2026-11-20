package br.com.xklpack.economy.command;

import br.com.xklpack.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;

public final class PayCommand implements CommandExecutor {
    private final EconomyService economy;

    public PayCommand(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por um jogador.");
            return true;
        }
        if (!player.hasPermission("xklpack.command.pay")) {
            player.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            player.sendMessage("§eUse: /pay <jogador> <valor> [coins|gems]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cO jogador precisa estar online para receber a transferência.");
            return true;
        }
        String currency = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "coins";
        try {
            BigDecimal amount = economy.parseAmount(currency, args[1]);
            economy.transfer(player.getUniqueId(), player.getName(), target.getUniqueId(),
                    target.getName(), currency, amount);
            String formatted = economy.format(currency, amount);
            player.sendMessage("§aVocê transferiu " + formatted + " para " + target.getName() + ".");
            target.sendMessage("§aVocê recebeu " + formatted + " de " + player.getName() + ".");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível concluir a transferência. Tente novamente.");
            exception.printStackTrace();
        }
        return true;
    }
}
