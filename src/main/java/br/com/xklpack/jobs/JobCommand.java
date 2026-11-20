package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JobCommand implements CommandExecutor, Listener {
    private final XKLPack plugin;
    private final JobService jobs;

    public JobCommand(XKLPack plugin, JobService jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("sair")) {
                jobs.leaveJob(player.getUniqueId());
                player.sendMessage("§aVocê saiu da profissão. Seu progresso foi preservado.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
                String active = jobs.getActiveJob(player.getUniqueId());
                if (active == null) player.sendMessage("§eVocê não tem uma profissão ativa.");
                else {
                    JobService.JobProgress progress = jobs.getProgress(player.getUniqueId(), active);
                    player.sendMessage("§6Profissão: §f" + jobs.getJobName(active) + " §7| §6Nível: §f" + progress.level()
                            + " §7| §6XP: §f" + progress.xp() + "/" + jobs.xpRequired(progress.level()));
                }
                return true;
            }
            openMenu(player);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Erro no sistema de profissões: " + exception.getMessage());
            player.sendMessage("§cNão foi possível carregar suas profissões. Veja o console.");
        }
        return true;
    }

    private void openMenu(Player player) throws SQLException {
        JobMenuHolder holder = new JobMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8Profissões §7• §6XKL");
        holder.inventory = inventory;
        String active = jobs.getActiveJob(player.getUniqueId());

        addJob(inventory, player, "minerador", Material.IRON_PICKAXE, 10);
        addJob(inventory, player, "lenhador", Material.IRON_AXE, 12);
        addJob(inventory, player, "fazendeiro", Material.IRON_HOE, 14);
        addJob(inventory, player, "cacador", Material.BOW, 16);
        addJob(inventory, player, "pescador", Material.FISHING_ROD, 22);

        inventory.setItem(4, item(Material.BOOK, "§e§lSua profissão",
                "§7Ativa: " + (active == null ? "§cNenhuma" : "§a" + jobs.getJobName(active)),
                "§7Clique em uma profissão para começar.",
                "§8Seu progresso fica salvo."));
        inventory.setItem(26, item(Material.BARRIER, "§cSair da profissão", "§7Clique para ficar sem profissão.", "§8O progresso não será apagado."));
        player.openInventory(inventory);
    }

    private void addJob(Inventory inventory, Player player, String id, Material icon, int slot) throws SQLException {
        if (!jobs.isValidJob(id)) return;
        JobService.JobProgress progress = jobs.getProgress(player.getUniqueId(), id);
        List<String> lore = new ArrayList<>();
        lore.add("§7Nível: §f" + progress.level() + " §8/ §f" + plugin.getConfig().getInt("jobs.max-level", 30));
        lore.add("§7Experiência: §f" + progress.xp() + "§7/§f" + jobs.xpRequired(progress.level()));
        lore.add("§7XP total: §f" + progress.totalXp());
        lore.add("");
        lore.add(progress.active() ? "§a§lPROFISSÃO ATIVA" : "§eClique para trabalhar nesta profissão");
        inventory.setItem(slot, item(icon, "§6§l" + jobs.getJobName(id), lore.toArray(String[]::new)));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        String id = switch (slot) {
            case 10 -> "minerador";
            case 12 -> "lenhador";
            case 14 -> "fazendeiro";
            case 16 -> "cacador";
            case 22 -> "pescador";
            default -> null;
        };
        try {
            if (slot == 26) {
                jobs.leaveJob(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§aVocê saiu da profissão. Seu progresso foi preservado.");
            } else if (id != null && jobs.isValidJob(id)) {
                jobs.selectJob(player.getUniqueId(), id);
                player.closeInventory();
                player.sendMessage("§aAgora você trabalha como §e" + jobs.getJobName(id) + "§a!");
                player.sendMessage("§7Use §f/job status §7para consultar seu progresso.");
            }
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cNão foi possível alterar sua profissão: " + exception.getMessage());
        }
    }

    private static final class JobMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
