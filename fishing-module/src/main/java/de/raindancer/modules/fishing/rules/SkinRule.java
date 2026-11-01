package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.RodSkin;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Which rod skins a fisher may wear: those their level and catches earned, and those whose token they used. */
public final class SkinRule implements IFishingRule {

    private final Catalog catalog;
    private final LevelRule levels;

    public SkinRule(Catalog catalog, LevelRule levels) {
        this.catalog = catalog;
        this.levels = levels;
    }

    public Set<RodSkin> unlocked(FisherProfile profile) {
        EnumSet<RodSkin> open = EnumSet.noneOf(RodSkin.class);
        for (RodSkin skin : RodSkin.values()) {
            if (has(skin, profile)) {
                open.add(skin);
            }
        }
        return open;
    }

    public boolean has(RodSkin skin, FisherProfile profile) {
        if (profile.hasSkin(skin)) {
            return true;
        }
        if (skin.level() > 0) {
            return levels.level(profile.experience()) >= skin.level();
        }
        if (skin.water() != null) {
            List<FishKind> all = catalog.fish().stream().filter(kind -> kind.livesIn(skin.water())).toList();
            return !all.isEmpty() && all.stream().allMatch(kind -> profile.caught(kind.id()) > 0);
        }
        return false;
    }

    /** How to get it, for the skins page. */
    public String how(RodSkin skin) {
        if (skin.level() > 0) {
            return "Reach fishing level " + skin.level() + ".";
        }
        if (skin.water() != null) {
            return "Catch every fish of the " + skin.water().title() + ".";
        }
        return "Only from a " + skin.chest().chestName() + ", as a token.";
    }

    @Override
    public String describe() {
        return "which rod skins a fisher may wear, and how the others are had";
    }
}
