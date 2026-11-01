package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;

import java.util.Locale;
import java.util.Random;

/**
 * How heavy a caught fish is — for bragging, nothing else. Each tier has its range (a mythic fish can weigh as much
 * as a boat), each kind sits a little above or below it, and the heavy end is rare: the top tenth is a whopper.
 */
public final class WeightRule implements IFishingRule {

    /** @param whopper among the heaviest tenth of its kind */
    public record Weighing(double kilos, boolean whopper) {
    }

    private static final double[][] RANGE = {
            {0.3, 4}, {1, 10}, {3, 28}, {8, 70}, {30, 260}, {150, 1200}};

    public Weighing weigh(FishKind kind, Random random) {
        double[] range = RANGE[kind.tier().ordinal()];
        // each kind its own build: up to a fifth lighter or heavier than its tier
        double build = 0.8 + 0.4 * ((kind.id().hashCode() & 0xff) / 255.0);
        double roll = random.nextDouble();
        double kilos = (range[0] + (range[1] - range[0]) * Math.pow(roll, 2.2)) * build;
        return new Weighing(kilos, roll >= 0.9);
    }

    public boolean worthTelling(Tier tier, Weighing weighing) {
        return weighing.whopper() && tier.atLeast(Tier.RARE);
    }

    public static String format(double kilos) {
        if (kilos < 1) {
            return Math.round(kilos * 1000) + " g";
        }
        return kilos >= 100 ? String.format(Locale.ROOT, "%.1f kg", kilos)
                : String.format(Locale.ROOT, "%.2f kg", kilos);
    }

    @Override
    public String describe() {
        return "how heavy a caught fish is, and whether it is a whopper worth telling everybody about";
    }
}
