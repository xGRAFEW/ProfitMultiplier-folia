package me.docdrewskii.profitmultiplier.api.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ThresholdReachedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Material material;
    private final double newTotal;
    private final double previousMultiplier;
    private final double newMultiplier;
    private final double threshold;

    /**
     * @param newTotal cumulative BASE revenue (money, not item count) earned on this ladder
     *                 after the sale that crossed the tier
     * @param threshold the (currency) threshold that was just crossed
     */
    public ThresholdReachedEvent(Player player, Material material, double newTotal,
                                 double previousMultiplier, double newMultiplier, double threshold) {
        this.player = player;
        this.material = material;
        this.newTotal = newTotal;
        this.previousMultiplier = previousMultiplier;
        this.newMultiplier = newMultiplier;
        this.threshold = threshold;
    }

    public Player getPlayer() {
        return player;
    }

    public Material getMaterial() {
        return material;
    }

    public double getNewTotal() {
        return newTotal;
    }

    public double getPreviousMultiplier() {
        return previousMultiplier;
    }

    public double getNewMultiplier() {
        return newMultiplier;
    }

    public double getThreshold() {
        return threshold;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
