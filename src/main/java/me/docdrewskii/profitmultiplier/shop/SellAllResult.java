package me.docdrewskii.profitmultiplier.shop;

public class SellAllResult {

    private final int itemsSold;
    private final int distinctTypes;
    private final double totalCredited;

    public SellAllResult(int itemsSold, int distinctTypes, double totalCredited) {
        this.itemsSold = itemsSold;
        this.distinctTypes = distinctTypes;
        this.totalCredited = totalCredited;
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public int getDistinctTypes() {
        return distinctTypes;
    }

    public double getTotalCredited() {
        return totalCredited;
    }

    public boolean isEmpty() {
        return itemsSold <= 0;
    }
}
