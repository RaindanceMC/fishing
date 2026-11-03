package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.SkillTree;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.TreeNode;

import java.util.Optional;

/**
 * Skill points and the skill tree: a point per fishing level, one more for each sea chest tier first opened and for
 * every ten kinds of fish caught; what a node costs, and whether a fisher may take another rank of it.
 */
public final class TreeRule implements IFishingRule {

    public enum Refusal { MAXED, BARRED, NEEDS, LEVEL, NO_POINTS }

    /** Kinds of fish caught per bonus point. */
    static final int KINDS_PER_POINT = 10;

    private final LevelRule levels;
    private final SkillTree tree;

    public TreeRule(LevelRule levels, SkillTree tree) {
        this.levels = levels;
        this.tree = tree;
    }

    public SkillTree tree() {
        return tree;
    }

    public LevelRule levels() {
        return levels;
    }

    public int bonus(FisherProfile profile) {
        int tiers = 0;
        for (Tier tier : Tier.values()) {
            tiers += profile.opened(tier) > 0 ? 1 : 0;
        }
        return tiers + profile.caught().size() / KINDS_PER_POINT;
    }

    public int points(FisherProfile profile) {
        return levels.level(profile.experience()) + bonus(profile);
    }

    public int spent(FisherProfile profile) {
        int spent = 0;
        for (var entry : profile.nodes().entrySet()) {
            Optional<TreeNode> node = tree.node(entry.getKey());
            if (node.isPresent()) {
                spent += node.get().cost() * Math.min(entry.getValue(), node.get().ranks());
            }
        }
        return spent;
    }

    public int left(FisherProfile profile) {
        return Math.max(0, points(profile) - spent(profile));
    }

    public Perks perks(FisherProfile profile) {
        return tree.perks(profile.nodes());
    }

    public NodeState state(FisherProfile profile, TreeNode node) {
        int rank = profile.rank(node.id());
        if (rank >= node.ranks()) {
            return NodeState.MAXED;
        }
        if (rank > 0) {
            return NodeState.OWNED;
        }
        if (barred(profile, node)) {
            return NodeState.BARRED;
        }
        return needsMet(profile, node) ? NodeState.OPEN : NodeState.LOCKED;
    }

    public Optional<Refusal> refusal(FisherProfile profile, TreeNode node) {
        int rank = profile.rank(node.id());
        if (rank >= node.ranks()) {
            return Optional.of(Refusal.MAXED);
        }
        if (rank == 0 && barred(profile, node)) {
            return Optional.of(Refusal.BARRED);
        }
        if (rank == 0 && !needsMet(profile, node)) {
            return Optional.of(Refusal.NEEDS);
        }
        if (levels.level(profile.experience()) < node.level()) {
            return Optional.of(Refusal.LEVEL);
        }
        if (left(profile) < node.cost()) {
            return Optional.of(Refusal.NO_POINTS);
        }
        return Optional.empty();
    }

    private boolean barred(FisherProfile profile, TreeNode node) {
        return node.excludes().stream().anyMatch(other -> profile.rank(other) > 0);
    }

    private static boolean needsMet(FisherProfile profile, TreeNode node) {
        return node.requires().isEmpty() || node.requires().stream().anyMatch(other -> profile.rank(other) > 0);
    }

    @Override
    public String describe() {
        return "skill points, and whether a node of the fishing skill tree may be taken";
    }
}
