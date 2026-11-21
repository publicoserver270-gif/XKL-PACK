package br.com.xklpack.pouch;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class PouchHolder implements InventoryHolder {
    private final UUID pouchId;
    private final UUID ownerId;
    private Inventory inventory;

    public PouchHolder(UUID pouchId, UUID ownerId) {
        this.pouchId = pouchId;
        this.ownerId = ownerId;
    }

    public UUID pouchId() { return pouchId; }
    public UUID ownerId() { return ownerId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
