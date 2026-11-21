package br.com.xklpack.bank;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor {
    private final BankMenu menu;
    public BankCommand(BankMenu menu){this.menu=menu;}
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("Este comando só pode ser usado por jogadores.");return true;}
        menu.open(player); return true;
    }
}
