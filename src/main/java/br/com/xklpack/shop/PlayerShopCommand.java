package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerShopCommand implements CommandExecutor, Listener {
    private final XKLPack plugin;
    private final PlayerShopService shops;

    public PlayerShopCommand(XKLPack plugin, PlayerShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("lojas")) {
                var ids = shops.listOwnedShops(player.getUniqueId());
                player.sendMessage("§6Suas lojas: §f" + (ids.isEmpty() ? "nenhuma" : ids));
                player.sendMessage("§7Painel: /lojaplayer painel <id>");
                return true;
            }
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "painel" -> {
                    requireArgs(args, 2, "/lojaplayer painel <id>");
                    openPanel(player, Long.parseLong(args[1]));
                }
                case "produto" -> handleProduct(player, args);
                case "loja" -> handleShopState(player, args);
                case "cofre" -> handleVault(player, args);
                case "aliado" -> handleAlly(player, args);
                default -> player.sendMessage("§6XKL LOJAS §7| painel, produto, cofre, aliado");
            }
        } catch (NumberFormatException exception) {
            player.sendMessage("§cNúmero inválido.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cFalha no banco de dados. Nenhuma operação foi concluída.");
            plugin.getLogger().severe("Erro nas lojas: " + exception.getMessage());
        }
        return true;
    }


    private void handleShopState(Player player, String[] args) throws SQLException {
        requireArgs(args, 3, "/lojaplayer loja <id> <abrir|fechar>");
        long id = Long.parseLong(args[1]);
        boolean open;
        if (args[2].equalsIgnoreCase("abrir")) open = true;
        else if (args[2].equalsIgnoreCase("fechar")) open = false;
        else throw new IllegalArgumentException("Use abrir ou fechar.");
        shops.setShopOpen(id, player.getUniqueId(), open);
        player.sendMessage(open ? "§aSua loja está aberta." : "§eSua loja está fechada.");
    }

    private void handleProduct(Player player, String[] args) throws SQLException {
        requireArgs(args, 3, "/lojaplayer produto <adicionar|listar|remover> <id> ...");
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        long shopId = Long.parseLong(args[2]);
        if (action.equals("adicionar")) {
            requireArgs(args, 5, "/lojaplayer produto adicionar <id> <quantidade> <preço>");
            int amount = Integer.parseInt(args[3]);
            int price = Integer.parseInt(args[4]);
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (amount < 1 || price < 1 || hand.getType().isAir() || hand.getAmount() < amount)
                throw new IllegalArgumentException("Segure o item e tenha a quantidade informada na mão.");
            ItemStack original = hand.clone();
            try {
                long offerId = shops.addOffer(shopId, player.getUniqueId(), hand, amount, price);
                if (hand.getAmount() == amount) player.getInventory().setItemInMainHand(null);
                else hand.setAmount(hand.getAmount() - amount);
                player.sendMessage("§aProduto #" + offerId + " cadastrado: " + amount + "x por " + price + " coins.");
            } catch (SQLException | RuntimeException failure) {
                player.getInventory().setItemInMainHand(original);
                throw failure;
            }
        } else if (action.equals("preco")) {
            requireArgs(args, 5, "/lojaplayer produto preco <id> <produto-id> <novo-preço>");
            shops.updateOfferPrice(shopId, Long.parseLong(args[3]), player.getUniqueId(), Integer.parseInt(args[4]));
            player.sendMessage("§aPreço do produto atualizado.");
        } else if (action.equals("listar")) {
            for (var offer : shops.listOffers(shopId, false)) {
                player.sendMessage("§e#" + offer.id() + " §f" + offer.item().getType()
                        + " §7" + offer.amount() + " un. §a" + offer.price() + " coins"
                        + (offer.enabled() ? " §aATIVO" : " §cESGOTADO"));
            }
        } else if (action.equals("remover")) {
            requireArgs(args, 4, "/lojaplayer produto remover <id> <produto-id>");
            shops.removeOffer(shopId, Long.parseLong(args[3]), player.getUniqueId());
            player.sendMessage("§aProduto removido.");
        } else throw new IllegalArgumentException("Use adicionar, listar ou remover.");
    }

    private void handleVault(Player player, String[] args) throws SQLException {
        requireArgs(args, 3, "/lojaplayer cofre <id> <saldo|retirar>");
        long shopId = Long.parseLong(args[1]);
        if (args[2].equalsIgnoreCase("saldo")) {
            player.sendMessage("§6Cofre da loja: §e" + shops.vaultBalance(shopId, player.getUniqueId()) + " coins físicas.");
            return;
        }
        if (!args[2].equalsIgnoreCase("retirar")) throw new IllegalArgumentException("Use saldo ou retirar.");
        BankService bank = plugin.getBankService();
        if (bank == null) throw new IllegalStateException("Banco de moedas físicas indisponível.");
        long amount = shops.vaultBalance(shopId, player.getUniqueId());
        if (amount <= 0) throw new IllegalArgumentException("O cofre está vazio.");
        ItemStack[] delivery = prepareCoinDelivery(player, bank, amount);
        long withdrawn = shops.withdrawVault(shopId, player.getUniqueId());
        if (withdrawn != amount) throw new IllegalStateException("O saldo do cofre mudou. Avise a administração.");
        player.getInventory().setStorageContents(delivery);
        player.sendMessage("§aRetirados " + withdrawn + " coins físicas do cofre.");
    }

    private ItemStack[] prepareCoinDelivery(Player player, BankService bank, long value) {
        List<ItemStack> items = new ArrayList<>();
        long left = value;
        for (var denomination : bank.denominations()) {
            long count = left / denomination.value();
            while (count > 0) {
                int stack = (int) Math.min(count, denomination.material().getMaxStackSize());
                items.add(bank.createCoin(denomination, stack));
                count -= stack;
            }
            left %= denomination.value();
        }
        if (left != 0) throw new IllegalStateException("As denominações não formam o valor do cofre.");
        Inventory simulated = Bukkit.createInventory(null, 36);
        ItemStack[] current = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[current.length];
        for (int i = 0; i < current.length; i++) copy[i] = current[i] == null ? null : current[i].clone();
        simulated.setContents(copy);
        if (!simulated.addItem(items.toArray(new ItemStack[0])).isEmpty())
            throw new IllegalArgumentException("Libere espaço no inventário antes de retirar as moedas.");
        return simulated.getStorageContents();
    }

    private void handleAlly(Player player, String[] args) throws SQLException {
        requireArgs(args, 3, "/lojaplayer aliado <adicionar|remover|listar|permissao> <id> ...");
        long shopId = Long.parseLong(args[2]);
        if (args[1].equalsIgnoreCase("listar")) {
            player.sendMessage("§6Aliados da loja: §f" + shops.listAllies(shopId, player.getUniqueId()));
            return;
        }
        if (args[1].equalsIgnoreCase("permissao")) {
            requireArgs(args, 6, "/lojaplayer aliado permissao <id> <jogador> <estoque|produtos|retirar|configurar> <on|off>");
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
            boolean enabled;
            if (args[5].equalsIgnoreCase("on")) enabled = true;
            else if (args[5].equalsIgnoreCase("off")) enabled = false;
            else throw new IllegalArgumentException("Use on ou off.");
            shops.setAllyPermission(shopId, player.getUniqueId(), target.getUniqueId(), args[4], enabled);
            player.sendMessage("§aPermissão atualizada para " + target.getName() + ".");
            return;
        }
        requireArgs(args, 4, "/lojaplayer aliado <adicionar|remover> <id> <jogador>");
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        if (args[1].equalsIgnoreCase("adicionar")) shops.addAlly(shopId, player.getUniqueId(), target.getUniqueId());
        else if (args[1].equalsIgnoreCase("remover")) shops.removeAlly(shopId, player.getUniqueId(), target.getUniqueId());
        else throw new IllegalArgumentException("Use adicionar, remover, listar ou permissao.");
        player.sendMessage("§aAliados atualizados.");
    }

    private void openPanel(Player player, long id) throws SQLException {
        if (!shops.isOwner(id, player.getUniqueId())
                && !shops.canManageProducts(id, player.getUniqueId())
                && !shops.canWithdraw(id, player.getUniqueId())
                && !shops.canConfigure(id, player.getUniqueId())
                && !shops.isAuthorizedForStock(id, player.getUniqueId()))
            throw new IllegalArgumentException("Você não tem acesso a esta loja.");
        PanelHolder holder = new PanelHolder(player.getUniqueId(), id);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8XKL | Loja #" + id);
        holder.inventory = inv;
        inv.setItem(10, button(Material.CHEST, "§6Estoque", "§7Baú ESTOQUE protegido no local da loja."));
        inv.setItem(12, button(Material.EMERALD, "§aProdutos e preços", "§7/lojaplayer produto adicionar " + id + " <qtd> <preço>"));
        inv.setItem(14, button(Material.GOLD_INGOT, "§eCofre", "§7/lojaplayer cofre " + id + " saldo ou retirar"));
inv.setItem(16, button(Material.PLAYER_HEAD, "§bAliados", "§7/lojaplayer aliado adicionar " + id + " <jogador>"));
        inv.setItem(22, button(Material.LEVER, "§eAbrir / fechar", "§7/lojaplayer loja " + id + " <abrir|fechar>"));
        player.openInventory(inv);
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void requireArgs(String[] args, int length, String usage) {
        if (args.length < length) throw new IllegalArgumentException("Uso: " + usage);
    }

    @EventHandler
    public void panelClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PanelHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 10) player.sendMessage("§eO baú ESTOQUE fica no local da loja.");
        if (event.getRawSlot() == 12) player.sendMessage("§eSegure o item e use /lojaplayer produto adicionar " + holder.shopId + " <quantidade> <preço>.");
        if (event.getRawSlot() == 14) player.sendMessage("§eUse /lojaplayer cofre " + holder.shopId + " saldo ou retirar.");
        if (event.getRawSlot() == 16) player.sendMessage("§eUse /lojaplayer aliado adicionar " + holder.shopId + " <jogador>.");
        if (event.getRawSlot() == 22) player.sendMessage("§eUse /lojaplayer loja " + holder.shopId + " abrir ou fechar.");
    }

    @EventHandler
    public void panelDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PanelHolder) event.setCancelled(true);
    }

    private static final class PanelHolder implements InventoryHolder {
        final UUID owner;
        final long shopId;
        Inventory inventory;
        PanelHolder(UUID owner, long shopId) { this.owner = owner; this.shopId = shopId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
