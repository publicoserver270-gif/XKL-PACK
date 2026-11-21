package br.com.xklpack.bank;

import br.com.xklpack.XKLPack;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BankListener implements Listener {
    private final XKLPack plugin; private final BankService bank; private final BankMenu menu;
    private final Map<UUID,Boolean> awaiting=new ConcurrentHashMap<>();
    public BankListener(XKLPack plugin,BankService bank,BankMenu menu){this.plugin=plugin;this.bank=bank;this.menu=menu;}

    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof BankMenu.Holder))return;
        event.setCancelled(true);
        if(event.getClickedInventory()!=top)return;
        int slot=event.getRawSlot();
        if(slot==plugin.getBankConfig().getInt("atm.menu.deposit-slot",11)){
            try{BigDecimal value=bank.depositAll(player); player.sendMessage(value.signum()>0?"§aDepósito realizado: §e"+value.toPlainString()+" Coins":"§cVocê não possui moedas XKL válidas.");}
            catch(Exception ex){player.sendMessage("§cFalha no depósito: "+ex.getMessage());}
            player.closeInventory();
        }else if(slot==plugin.getBankConfig().getInt("atm.menu.withdraw-slot",13)){
            awaiting.put(player.getUniqueId(),true); player.closeInventory(); player.sendMessage("§eDigite no chat o valor inteiro de Coins que deseja sacar. Digite §ccancelar §epara desistir.");
        }else if(slot==plugin.getBankConfig().getInt("atm.menu.statement-slot",15)){
            player.closeInventory(); player.sendMessage("§6§lEXTRATO DO BANCO");
            try{var rows=bank.statement(player.getUniqueId()); if(rows.isEmpty())player.sendMessage("§7Nenhuma movimentação encontrada."); else rows.forEach(r->player.sendMessage(ChatColor.translateAlternateColorCodes('&',r)));}
            catch(SQLException ex){player.sendMessage("§cNão foi possível consultar o extrato.");}
        }else if(slot==plugin.getBankConfig().getInt("atm.menu.fees-slot",22)){
            player.sendMessage("§eTaxa de depósito: §f"+plugin.getBankConfig().getDouble("atm.fees.deposit-percent",0)+"%");
            player.sendMessage("§eTaxa de saque: §f"+plugin.getBankConfig().getDouble("atm.fees.withdraw-percent",2)+"%");
        }
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof BankMenu.Holder)event.setCancelled(true);}
    @EventHandler public void chat(AsyncPlayerChatEvent event){
        Player player=event.getPlayer(); if(!awaiting.remove(player.getUniqueId(),true))return;
        event.setCancelled(true); String input=event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(input.equalsIgnoreCase("cancelar")){player.sendMessage("§eSaque cancelado.");return;}
            try{BigDecimal value=new BigDecimal(input.replace(",",".")); BigDecimal paid=bank.withdraw(player,value); player.sendMessage("§aSaque entregue: §e"+paid.toPlainString()+" Coins");}
            catch(Exception ex){player.sendMessage("§cSaque não realizado: "+ex.getMessage());}
            menu.open(player);
        });
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){awaiting.remove(event.getPlayer().getUniqueId());}
}
