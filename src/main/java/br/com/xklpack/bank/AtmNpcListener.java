package br.com.xklpack.bank;

import br.com.xklpack.XKLPack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.lang.reflect.Method;
import java.util.List;

public final class AtmNpcListener implements Listener {
    private final XKLPack plugin;
    private final BankMenu menu;

    public AtmNpcListener(XKLPack plugin, BankMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        if (!plugin.getBankConfig().getBoolean("citizens.enabled", true)
                || !plugin.getBankConfig().getBoolean("citizens.click-to-open", true)
                || !plugin.getBankConfig().getBoolean("citizens.require-linked-npc", true)) {
            return;
        }

        Entity entity = event.getRightClicked();
        if (!entity.hasMetadata("NPC")) return;

        Integer npcId = resolveNpcId(entity);
        if (npcId == null) return;

        List<Integer> linked = plugin.getBankConfig().getIntegerList("citizens.linked-npcs");
        if (!linked.contains(npcId)) return;

        Player player = event.getPlayer();
        menu.open(player);
    }

    private Integer resolveNpcId(Entity entity) {
        try {
            Class<?> citizensApi = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = citizensApi.getMethod("getNPCRegistry").invoke(null);
            if (registry == null) return null;
            Method getNpc = registry.getClass().getMethod("getNPC", Entity.class);
            Object npc = getNpc.invoke(registry, entity);
            if (npc == null) return null;
            Object id = npc.getClass().getMethod("getId").invoke(npc);
            return id instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().fine("Não foi possível identificar o NPC do Citizens: " + ex.getMessage());
            return null;
        }
    }
}
