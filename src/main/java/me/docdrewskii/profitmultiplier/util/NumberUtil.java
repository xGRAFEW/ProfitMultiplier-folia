package me.docdrewskii.profitmultiplier.util;

import java.text.DecimalFormat;

public final class NumberUtil {

    private static final DecimalFormat COMMA = new DecimalFormat("#,##0");
    private static final DecimalFormat COMMA_DECIMAL = new DecimalFormat("#,##0.##");
    private static final DecimalFormat MULT = new DecimalFormat("0.##");
    private static final DecimalFormat CHANGE_PERCENT = new DecimalFormat("0.#");

    private NumberUtil() {
    }

    public static String commas(long value) {
        return COMMA.format(value);
    }

    public static String commas(double value) {
        return COMMA_DECIMAL.format(value);
    }

    public static String multiplier(double value) {
        return MULT.format(value);
    }

    public static String abbreviate(long value) {
        if (value < 1000L) return Long.toString(value);
        if (value < 1_000_000L) return trim(value / 1_000.0) + "K";
        if (value < 1_000_000_000L) return trim(value / 1_000_000.0) + "M";
        if (value < 1_000_000_000_000L) return trim(value / 1_000_000_000.0) + "B";
        return trim(value / 1_000_000_000_000.0) + "T";
    }

    public static String abbreviate(double value) {
        if (value < 1000.0) return trim(value);
        if (value < 1_000_000.0) return trim(value / 1_000.0) + "K";
        if (value < 1_000_000_000.0) return trim(value / 1_000_000.0) + "M";
        if (value < 1_000_000_000_000.0) return trim(value / 1_000_000_000.0) + "B";
        return trim(value / 1_000_000_000_000.0) + "T";
    }

    public static String progressBar(long current, long goal, int length, char symbol,
                                     String completeColor, String incompleteColor) {
        return progressBar((double) current, (double) goal, length, symbol, completeColor, incompleteColor);
    }

    public static String progressBar(double current, double goal, int length, char symbol,
                                     String completeColor, String incompleteColor) {
        int filled;
        if (goal <= 0) {
            filled = length;
        } else {
            double ratio = current / goal;
            if (ratio < 0) ratio = 0;
            if (ratio > 1) ratio = 1;
            filled = (int) Math.round(ratio * length);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(completeColor);
        for (int i = 0; i < length; i++) {
            if (i == filled) sb.append(incompleteColor);
            sb.append(symbol);
        }
        return sb.toString();
    }

    public static int percent(long current, long goal) {
        return percent((double) current, (double) goal);
    }

    public static int percent(double current, double goal) {
        if (goal <= 0) return 100;
        double ratio = current / goal;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return (int) Math.round(ratio * 100);
    }

    private static String trim(double value) {
        return MULT.format(value);
    }

    /** Percentage change of {@code current} relative to {@code standard} (e.g. +12.5, -8.0). */
    public static double percentChange(double standard, double current) {
        if (standard == 0) return 0.0;
        return ((current - standard) / standard) * 100.0;
    }

    /** Formats a percent-change value with an explicit sign, e.g. "+12.5%", "-8%", "0%". */
    public static String signedPercent(double changePercent) {
        if (Math.abs(changePercent) < 0.05) return "0%";
        String sign = changePercent > 0 ? "+" : "";
        return sign + CHANGE_PERCENT.format(changePercent) + "%";
    }

    /** A colored, arrow-prefixed price-change indicator suitable for menu lore, e.g. "&a▲ +12.5%". */
    public static String priceChangeIndicator(double changePercent) {
        if (Math.abs(changePercent) < 0.05) return "&7■ 0%";
        if (changePercent > 0) return "&a▲ " + signedPercent(changePercent);
        return "&c▼ " + signedPercent(changePercent);
    }
}
