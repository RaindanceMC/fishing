package de.raindancer.modules.fishing.model;

import de.raindancer.core.ui.tree.SkillTree;

/** The fishing skill tree, {@code skilltree.yml} in the jar; its effects are {@link Perk} ids. */
public final class FishingTree {

    private static volatile SkillTree shipped;

    private FishingTree() {
    }

    public static SkillTree shipped() {
        SkillTree known = shipped;
        if (known == null) {
            known = SkillTree.resource(FishingTree.class, "/de/raindancer/modules/fishing/skilltree.yml", Perk::known);
            shipped = known;
        }
        return known;
    }
}
