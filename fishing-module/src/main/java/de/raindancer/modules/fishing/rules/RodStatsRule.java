package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Water;

import java.util.ArrayList;
import java.util.List;

/**
 * What a rod does against a plain fishing rod — its tier, upgrades and fishing enchantments added up — as the lines
 * its lore shows. The fisher's own skills are theirs, not the rod's, and are left out.
 */
public final class RodStatsRule implements IFishingRule {

    private final BiteRule bites = new BiteRule();

    public List<String> lines(Rod rod) {
        List<String> lines = new ArrayList<>();
        if (rod.isPlain()) {
            return lines;
        }
        long sooner = Math.round((1 - bites.waitFactor(rod, FisherProfile.NEW, false)) * 100);
        if (sooner > 0) {
            lines.add("<gray>Fish bite <white>" + sooner + "%</white> sooner than with a plain rod");
        }
        int chests = rod.rank(RodPart.LURE) * 15 + rod.ownChestPermille();
        if (chests > 0) {
            lines.add("<gray>Sea chests: <white>+" + permille(chests) + "%</white> a cast");
        }
        long rarer = Math.round(rod.ownRareBonus() * 100);
        if (rarer > 0) {
            lines.add("<gray>Rarer catches: <white>+" + rarer + "%");
        }
        int doubles = rod.rank(RodPart.REEL) * 50 + rod.ownDoublePermille();
        if (doubles > 0) {
            lines.add("<gray>Double catches: <white>" + permille(doubles) + "%</white> of fish");
        }
        if (rod.fishes(Water.VOID)) {
            lines.add("<gold>Fishes lava and the void");
        } else if (rod.fishes(Water.LAVA)) {
            lines.add("<gold>Fishes lava");
        }
        List<String> fitted = new ArrayList<>();
        for (RodPart part : RodPart.values()) {
            if (rod.rank(part) > 0) {
                fitted.add(part.rankName(rod.rank(part)));
            }
        }
        if (!fitted.isEmpty()) {
            lines.add("<dark_gray>Upgrades: " + String.join(", ", fitted));
        }
        return lines;
    }

    private static String permille(int permille) {
        return permille % 10 == 0 ? String.valueOf(permille / 10) : String.valueOf(permille / 10.0);
    }

    @Override
    public String describe() {
        return "what a rod does against a plain one, for its lore";
    }
}
