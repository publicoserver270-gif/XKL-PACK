package br.com.xklpack.shop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import java.util.HashMap;
import java.util.Map;

import java.sql.SQLException;
import java.util.UUID;

public final class PlayerShopCommand implements CommandExecutor, Listener {
    private final PlayerShopService shops;\n    private final XKLPack plugin;

    public PlayerShopCommand(PlayerShopService shops) {
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
                player.sendMessage("§6Suas lojas: §f" + (ids.isEmpty() ? "nenhuma" : ids.toString()));
                player.sendMessage("§7Use /lojaplayer aliado <adicionar|remover|listar|permissao> <id> [jogador]");
                return true;
            }
            if (!args[0].equalsIgnoreCase("aliado") || args.length < 3) {
                player.sendMessage("§cUso: /lojaplayer aliado <adicionar|remover|listar|permissao> <id> [jogador]");
                return true;
            }
            long shopId = Long.parseLong(args[2]);
            if (args[1].equalsIgnoreCase("listar")) {
                var allies = shops.listAllies(shopId, player.getUniqueId());
                player.sendMessage("§6Aliados da loja #" + shopId + ": §f" + allies);
                return true;
            }
            if (args[1].equalsIgnoreCase("permissao") || args[1].equalsIgnoreCase("permissão")) {
                if (args.length < 6) {
                    player.sendMessage("§cUso: /lojaplayer aliado permissao <id> <jogador> <estoque|produtos|retirar|configurar> <on|off>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
                boolean enabled;
                if (args[5].equalsIgnoreCase("on") || args[5].equalsIgnoreCase("ligar")) enabled = true;
                else if (args[5].equalsIgnoreCase("off") || args[5].equalsIgnoreCase("desligar")) enabled = false;
                else {
                    player.sendMessage("§cUse on ou off.");
                    return true;
                }
                shops.setAllyPermission(shopId, player.getUniqueId(), target.getUniqueId(), args[4], enabled);
                player.sendMessage("§aPermissão " + args[4] + " atualizada para " + target.getName() + ".");
                return true;
            }
            if (args.length < 4) {
                player.sendMessage("§cInforme o nome do jogador.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
            UUID targetId = target.getUniqueId();
            if (args[1].equalsIgnoreCase("adicionar") || args[1].equalsIgnoreCase("add")) {
                shops.addAlly(shopId, player.getUniqueId(), targetId);
                player.sendMessage("§aAliado adicionado. Ele recebeu acesso ao estoque, produtos, configuração e retirada.");
            } else if (args[1].equalsIgnoreCase("remover") || args[1].equalsIgnoreCase("remove")) {
                shops.removeAlly(shopId, player.getUniqueId(), targetId);
                player.sendMessage("§aAliado removido da loja.");
            } else {
                player.sendMessage("§cAção inválida. Use adicionar, remover ou listar.");
            }
        } catch (NumberFormatException exception) {
            player.sendMessage("§cID de loja inválido.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível executar a operação.");
        }
        return true;
    }
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
                player.sendMessage("§6Suas lojas: §f" + (ids.isEmpty() ? "nenhuma" : ids.toString()));
                player.sendMessage("§7Use /lojaplayer painel <id> para gerenciar.");
                return true;
            }
            if (args[0].equalsIgnoreCase("painel") && args.length >= 2) {
                openPanel(player, Long.parseLong(args[1]));
                return true;
            }
            if (args[0].equalsIgnoreCase("produto") && args.length >= 2) {
                long id = Long.parseLong(args[2]);
                if (args[1].equalsIgnoreCase("adicionar")) {
                    if (args.length < 5) { player.sendMessage("§c/lojaplayer produto adicionar <id> <quantidade> <preco>"); return true; }
                    int amount = Integer.parseInt(args[3]), price = Integer.parseInt(args[4]);
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir() || hand.getAmount() < amount) throw new IllegalArgumentException("Segure o item e tenha a quantidade definida na mão.");
                    long offer = shops.addOffer(id, player.getUniqueId(), hand, amount, price);
                    hand.setAmount(hand.getAmount() - amount);
                    player.sendMessage("§aProduto #" + offer + " cadastrado: " + amount + "x por " + price + " coins.");
                } else if (args[1].equalsIgnoreCase("remover") && args.length >= 4) {
                    shops.removeOffer(id, Long.parseLong(args[3]), player.getUniqueId());
                    player.sendMessage("§aProduto removido.");
                } else if (args[1].equalsIgnoreCase("listar")) {
                    for (var offer : shops.listOffers(id, false))
                        player.sendMessage("§e#" + offer.id() + " §f" + offer.item().getType() + " §7" + offer.amount() + " un. §a" + offer.price() + " coins §8" + (offer.enabled() ? "ATIVO" : "ESGOTADO"));
                } else player.sendMessage("§c/lojaplayer produto <adicionar|remover|listar> <id> ...");
                return true;
            }
            if (args[0].equalsIgnoreCase("cofre") && args.length >= 3) {
                long id = Long.parseLong(args[1]);
                if (args[2].equalsIgnoreCase("saldo")) {
                    player.sendMessage("§6Cofre: §e" + shops.vaultBalance(id, player.getUniqueId()) + " coins físicas.");
                } else if (args[2].equalsIgnoreCase("retirar")) {
                    long amount = shops.withdrawVault(id, player.getUniqueId());
                    deliverCoins(player, amount);
                    player.sendMessage("§aRetirados " + amount + " coins físicas do cofre.");
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("aliado") && args.length >= 3) {
                handleAlly(player, args);
                return true;
            }
            player.sendMessage("§6XKL LOJAS §7| /lojaplayer painel <id> | produto adicionar/listar/remover | cofre saldo/retirar | aliado ...");
        } catch (NumberFormatException exception) {
            player.sendMessage("§cNúmero inválido.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cFalha no banco de dados. Tente novamente.");
            plugin.getLogger().severe("Erro nas lojas: " + exception.getMessage());
        }
        return true;
    }

    private void handleAlly(Player player, String[] args) throws SQLException {
        if (args.length < 3) return;
        long shopId = Long.parseLong(args[2]);
        if (args[1].equalsIgnoreCase("listar")) {
            player.sendMessage("§6Aliados: §f" + shops.listAllies(shopId, player.getUniqueId())); return;
        }
        if (args[1].equalsIgnoreCase("permissao") && args.length >= 6) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
            boolean enabled = args[5].equalsIgnoreCase("on") || args[5].equalsIgnoreCase("ligar");
            shops.setAllyPermission(shopId, player.getUniqueId(), target.getUniqueId(), args[4], enabled);
            player.sendMessage("§aPermissão atualizada."); return;
        }
        if (args.length < 4) { player.sendMessage("§cInforme o jogador."); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        if (args[1].equalsIgnoreCase("adicionar")) shops.addAlly(shopId, player.getUniqueId(), target.getUniqueId());
        else if (args[1].equalsIgnoreCase("remover")) shops.removeAlly(shopId, player.getUniqueId(), target.getUniqueId());
        else throw new IllegalArgumentException("Ação de aliado inválida.");
        player.sendMessage("§aLista de aliados atualizada.");
    }

    private void openPanel(Player player, long id) throws SQLException {
        if (!shops.isOwner(id, player.getUniqueId()) && !shops.canManageProducts(id, player.getUniqueId())
                && !shops.canWithdraw(id, player.getUniqueId()) && !shops.isAuthorizedForStock(id, player.getUniqueId()))
            throw new IllegalArgumentException("Você não tem acesso a esta loja.");
        PanelHolder holder = new PanelHolder(player.getUniqueId(), id);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8XKL | Loja #" + id);
        holder.inventory = inv;
        inv.setItem(10, button(Material.CHEST, "§6Estoque", "§7Acessar o baú protegido ESTOQUE."));
        inv.setItem(12, button(Material.EMERALD, "§aProdutos e preços", "§7Use /lojaplayer produto adicionar " + id + " <qtd> <preço>"));
        inv.setItem(14, button(Material.GOLD_INGOT, "§eCofre", "§7Use /lojaplayer cofre " + id + " saldo/retirar"));
        inv.setItem(16, button(Material.PLAYER_HEAD, "§bAliados", "§7Use /lojaplayer aliado ... " + id));
        player.openInventory(inv);
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(java.util.List.of(lore)); item.setItemMeta(meta);
        return item;
    }

    private void deliverCoins(Player player, long value) {
        BankService bank = plugin.getBankService();
        if (bank == null) throw new IllegalStateException("Banco de moedas físicas indisponível.");
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        long left = value;
        for (var d : bank.denominations()) {
            long count = left / d.value();
            while (count > 0) {
                int stack = (int)Math.min(count, d.material().getMaxStackSize());
                items.add(bank.createCoin(d, stack)); count -= stack;
            }
            left %= d.value();
        }
        if (left != 0) throw new IllegalStateException("Denominações não formam o valor do cofre.");
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] current = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[current.length];
        for (int i=0;i<current.length;i++) copy[i] = current[i] == null ? null : current[i].clone();
        sim.setContents(copy);
        if (!sim.addItem(items.toArray(new ItemStack[0])).isEmpty()) throw new IllegalArgumentException("Libere espaço no inventário para receber as moedas.");
        player.getInventory().setStorageContents(sim.getStorageContents());
    }

    @EventHandler
    public void panelClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PanelHolder holder)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p && holder.owner.equals(p.getUniqueId())) {
            if (event.getRawSlot() == 10) p.sendMessage("§eO estoque ESTOQUE fica no local da sua loja.");
            if (event.getRawSlot() == 12) p.sendMessage("§eSegure o item e use /lojaplayer produto adicionar " + holder.shopId + " <quantidade> <preço>.");
            if (event.getRawSlot() == 14) p.sendMessage("§eUse /lojaplayer cofre " + holder.shopId + " saldo ou retirar.");
            if (event.getRawSlot() == 16) p.sendMessage("§eUse /lojaplayer aliado adicionar " + holder.shopId + " <jogador>.");
        }
    }

    @EventHandler
    public void panelDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PanelHolder) event.setCancelled(true);
    }

    private static final class PanelHolder implements InventoryHolder {
        final UUID owner; final long shopId; Inventory inventory;
        PanelHolder(UUID owner, long shopId) { this.owner=owner; this.shopId=shopId; }
        @Override public Inventory getInventory() { return inventory; }
    }

