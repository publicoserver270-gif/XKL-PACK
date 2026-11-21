package br.com.xklpack.pouch;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MoneyPouchCommand implements CommandExecutor {
    private final MoneyPouchService pouches;

    public MoneyPouchCommand(MoneyPouchService pouches) { this.pouches = pouches; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xklpack.admin.pouch")) {
            sender.sendMessage("§cVocê não tem permissão.");
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eUso: /xklpouch give <jogador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJogador precisa estar online.");
            return true;
        }
        try {
            ItemStack pouch = pouches.createPouch(target);
            var overflow = target.getInventory().addItem(pouch);
            overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            sender.sendMessage("§aSaco de Coins entregue para " + target.getName() + ".");
            target.sendMessage("§aVocê recebeu um Saco de Coins XKL.");
        } catch (Exception ex) {
            sender.sendMessage("§cNão foi possível criar o saco: " + ex.getMessage());
        }
        return true;
    }
}
