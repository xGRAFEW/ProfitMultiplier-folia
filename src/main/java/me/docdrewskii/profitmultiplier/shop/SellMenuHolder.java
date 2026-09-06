package me.docdrewskii.profitmultiplier.shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class SellMenuHolder implements InventoryHolder {

    private final UUID viewer;
    private Inventory inventory;

    public SellMenuHolder(Player viewer) {
        this.viewer = viewer.getUniqueId();
    }

    public UUID getViewer() {
        return viewer;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
