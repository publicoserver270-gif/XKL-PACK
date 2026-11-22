package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ExcavatorListener implements Listener {
    private final NamespacedKey excavatorKey;
    private final Set<UUID> processing = new HashSet<>();

    public ExcavatorListener(XKLPack plugin) {
        this.excavatorKey = new NamespacedKey(plugin, "excavator_3x3");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (processing.contains(uuid)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isExcavator(tool)) return;

        processing.add(uuid);
        try {
            for (Block target : area(event.getBlock(), player)) {
                if (target.equals(event.getBlock()) || !canExcavate(target)) continue;

                // Dispara um evento próprio para cada bloco adicional. Proteções como
                // EasyClaim/WorldGuard podem cancelar cada quebra individualmente.
                BlockBreakEvent extra = new BlockBreakEvent(target, player);
                Bukkit.getPluginManager().callEvent(extra);
                if (extra.isCancelled()) continue;

                int experience = extra.getExpToDrop();
                boolean dropItems = extra.isDropItems();
                boolean broken;
                if (dropItems) {
                    broken = target.breakNaturally(tool);
                } else {
                    target.setType(Material.AIR, false);
                    broken = true;
                }
                if (broken && experience > 0) {
                    ExperienceOrb orb = target.getWorld().spawn(target.getLocation(), ExperienceOrb.class);
                    orb.setExperience(experience);
                }
                if (broken && !damageExtraUse(player, tool)) break;
                tool = player.getInventory().getItemInMainHand();
                if (!isExcavator(tool)) break;
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private boolean isExcavator(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(excavatorKey, PersistentDataType.BYTE);
    }

    private boolean canExcavate(Block block) {
        if (block.getType().isAir() || !block.getType().isSolid()) return false;
        if (block.getState() instanceof Container) return false;
        return switch (block.getType()) {
            case BEDROCK, BARRIER, END_PORTAL, END_PORTAL_FRAME, NETHER_PORTAL,
                    REINFORCED_DEEPSLATE, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK,
                    REPEATING_COMMAND_BLOCK, STRUCTURE_BLOCK, JIGSAW -> false;
            default -> true;
        };
    }

    private Set<Block> area(Block origin, Player player) {
        Set<Block> blocks = new HashSet<>();
        Vector direction = player.getLocation().getDirection();
        if (Math.abs(direction.getY()) > 0.7) {
            // Olhando para cima/baixo: plano horizontal X/Z.
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) blocks.add(origin.getRelative(x, 0, z));
            }
        } else if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            // Olhando mais para leste/oeste: plano Z/Y.
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) blocks.add(origin.getRelative(0, y, z));
            }
        } else {
            // Olhando mais para norte/sul: plano X/Y.
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) blocks.add(origin.getRelative(x, y, 0));
            }
        }
        return blocks;
    }

    private boolean damageExtraUse(Player player, ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return true;
        int maxDurability = tool.getType().getMaxDurability();
        if (maxDurability <= 0) return true;

        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && Math.random() < (double) unbreaking / (unbreaking + 1)) return true;

        int nextDamage = damageable.getDamage() + 1;
        if (nextDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage("§cSua Picareta Escavadora 3x3 quebrou.");
            return false;
        }
        damageable.setDamage(nextDamage);
        tool.setItemMeta(meta);
        player.getInventory().setItemInMainHand(tool);
        return true;
    }
}
