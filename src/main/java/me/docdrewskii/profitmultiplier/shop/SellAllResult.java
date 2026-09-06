package me.docdrewskii.profitmultiplier.shop;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SellAllResult {

    public static class Entry {
        private final Material material;
        private final int amount;
        private final double credited;

        public Entry(Material material, int amount, double credited) {
            this.material = material;
            this.amount = amount;
            this.credited = credited;
        }

        public Material getMaterial() {
            return material;
        }

        public int getAmount() {
            return amount;
        }

        public double getCredited() {
            return credited;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int itemsSold;
    private double totalCredited;

    public void add(Material material, int amount, double credited) {
        entries.add(new Entry(material, amount, credited));
        itemsSold += amount;
        totalCredited += credited;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public int getDistinctTypes() {
        return entries.size();
    }

    public double getTotalCredited() {
        return totalCredited;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
