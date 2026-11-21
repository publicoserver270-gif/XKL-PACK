package br.com.xklpack.bank;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class BankMenu {
    private final XKLPack plugin;
    private final BankService bank;
    public BankMenu(XKLPack plugin, BankService bank) { this.plugin=plugin; this.bank=bank; }

    public void open(PlayerHolder holder) {}
    public void open(org.bukkit.entity.Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getBankConfig().getInt("atm.rows", 3)));
        Inventory inv = Bukkit.createInventory(new Holder(), rows * 9, color(plugin.getBankConfig().getString("atm.title", "&8Banco XKL")));
        set(inv, plugin.getBankConfig().getInt("atm.menu.deposit-slot",11), Material.HOPPER, "&aDepositar moedas", "&7Deposita todas as moedas XKL", "&eClique para depositar");
        set(inv, plugin.getBankConfig().getInt("atm.menu.withdraw-slot",13), Material.GOLD_INGOT, "&6Sacar Coins", "&7Digite o valor no chat", "&eClique para escolher");
        set(inv, plugin.getBankConfig().getInt("atm.menu.statement-slot",15), Material.BOOK, "&bExtrato", "&7Últimas movimentações", "&eClique para consultar");
        set(inv, plugin.getBankConfig().getInt("atm.menu.fees-slot",22), Material.PAPER, "&eTaxas", "&7Depósito: &f"+plugin.getBankConfig().getDouble("atm.fees.deposit-percent",0)+"%", "&7Saque: &f"+plugin.getBankConfig().getDouble("atm.fees.withdraw-percent",2)+"%");
        player.openInventory(inv);
    }
    private void set(Inventory inv,int slot,Material mat,String name,String... lore) {
        if(slot<0 || slot>=inv.getSize()) return;
        ItemStack item=new ItemStack(mat); ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(color(name)); meta.setLore(java.util.Arrays.stream(lore).map(this::color).toList());
        item.setItemMeta(meta); inv.setItem(slot,item);
    }
    private String color(String s){return org.bukkit.ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    public static final class Holder implements InventoryHolder { public Inventory getInventory(){return null;} }
    public static final class PlayerHolder {}
}
