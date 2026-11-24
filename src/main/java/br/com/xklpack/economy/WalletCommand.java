package br.com.xklpack.economy;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WalletCommand implements CommandExecutor, Listener {
    private final XKLPack plugin;
    private final EconomyService economy;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    public WalletCommand(XKLPack plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        openMain(player);
        return true;
    }

    private void openMain(Player player) {
        WalletHolder holder = new WalletHolder(player.getUniqueId(), "main");
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8§lCARTEIRA XKL");
        holder.inventory = inventory;
        try {
            BigDecimal coins = economy.getBalance(player.getUniqueId(), "coins");
            BigDecimal gems = economy.getBalance(player.getUniqueId(), "gems");
            inventory.setItem(11, button(Material.GOLD_INGOT, "§6§lCOINS",
                    List.of("§7Saldo virtual disponível:", "§e" + economy.format("coins", coins),
                            "", "§8Usadas na loja e em transferências.")));
            inventory.setItem(15, button(Material.AMETHYST_SHARD, "§d§lGEMAS",
                    List.of("§7Saldo de gemas:", "§d" + economy.format("gems", gems),
                            "", "§8Obtidas por atividades profissionais.", "§8Usadas exclusivamente em ferramentas.")));
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cNão foi possível consultar sua carteira: " + exception.getMessage());
            return;
        }
        inventory.setItem(22, button(Material.BOOK, "§b§lHISTÓRICO",
                List.of("§7Últimas movimentações das moedas virtuais.", "§eClique para consultar.")));
        player.openInventory(inventory);
    }

    private void openHistory(Player player) {
        WalletHolder holder = new WalletHolder(player.getUniqueId(), "history");
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8§lCARTEIRA • HISTÓRICO");
        holder.inventory = inventory;
        try {
            List<EconomyService.TransactionEntry> rows = economy.getRecentTransactions(player.getUniqueId(), 45);
            int slot = 0;
            for (EconomyService.TransactionEntry row : rows) {
                boolean incoming = "IN".equals(row.direction());
                Material icon = "gems".equalsIgnoreCase(row.currency())
                        ? Material.AMETHYST_SHARD : Material.GOLD_NUGGET;
                String amount = economy.format(row.currency(), row.amount());
                List<String> lore = List.of(
                        "§7Movimento: " + (incoming ? "§aEntrada" : "§cSaída"),
                        "§7Valor: §f" + amount,
                        "§7Data: §f" + DATE.format(Instant.ofEpochMilli(row.createdAt())),
                        "§7Tipo: §f" + label(row.type())
                );
                inventory.setItem(slot++, button(icon, (incoming ? "§a" : "§c") + label(row.type()), lore));
            }
            if (rows.isEmpty()) {
                inventory.setItem(22, button(Material.BARRIER, "§cNenhuma movimentação",
                        List.of("§7Sua carteira ainda não possui transações.")));
            }
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível consultar o histórico: " + exception.getMessage());
            return;
        }
        inventory.setItem(49, button(Material.ARROW, "§eVoltar à carteira", List.of()));
        player.openInventory(inventory);
    }

    private String label(String type) {
        return switch (type) {
            case "JOB_SALARY" -> "Salário profissional";
            case "SELL_REVENUE" -> "Venda na loja";
            case "TRANSFER" -> "Transferência";
            case "TOOL_PURCHASE" -> "Compra de ferramenta";
            case "SHOP_PURCHASE" -> "Compra na loja";
            case "JOB_ACTIVITY" -> "Recompensa profissional";
            case "ATM_DEPOSIT" -> "Depósito no banco";
            case "ATM_WITHDRAW" -> "Saque do banco";
            case "ATM_FEE" -> "Taxa bancária";
            default -> type.replace('_', ' ');
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WalletHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if ("main".equals(holder.page) && event.getRawSlot() == 22) openHistory(player);
        else if ("history".equals(holder.page) && event.getRawSlot() == 49) openMain(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WalletHolder) event.setCancelled(true);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class WalletHolder implements InventoryHolder {
        private final UUID owner;
        private final String page;
        private Inventory inventory;

        private WalletHolder(UUID owner, String page) {
            this.owner = owner;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
