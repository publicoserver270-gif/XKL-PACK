package br.com.xklpack.bank;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor {
    private final BankMenu menu;

    public BankCommand(BankMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }

        // Jogadores comuns acessam o banco somente clicando em um NPC do Citizens
        // vinculado pela administração. O comando direto fica reservado à equipe.
        if (!player.hasPermission("xklpack.admin.atm")) {
            player.sendMessage("§cUse um caixa eletrônico do Banco XKL para acessar sua conta.");
            return true;
        }

        menu.open(player);
        return true;
    }
}
