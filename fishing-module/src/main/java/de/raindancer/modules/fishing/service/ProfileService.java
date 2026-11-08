package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.FishingTree;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.rules.LevelRule;
import de.raindancer.modules.fishing.rules.TreeRule;
import de.raindancer.modules.fishing.store.ProfileStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A fisher's experience, level and skills: earning, spending, and telling them when they go up.
 *
 * <p>Reading a profile answers from memory. Earning (catches, experience) is kept in memory and written in batches
 * (every few seconds, when the player leaves, and when the module stops); spending (skills, skins, fees) goes to the
 * database at once, off the server's threads, and comes back to the player's own thread to tell them how it went.
 */
public final class ProfileService implements IFishingService {

    private static final de.raindancer.modules.fishing.rules.BonusRule BONUS =
            new de.raindancer.modules.fishing.rules.BonusRule();

    private final Plugin plugin;
    private final ProfileStore store;
    private final Messages messages;
    private final Effects effects;
    private volatile LevelRule levels;
    private volatile TreeRule tree;

    public ProfileService(Plugin plugin, ProfileStore store, Messages messages, Effects effects,
                          FishingSettings settings) {
        this.plugin = plugin;
        this.store = store;
        this.messages = messages;
        this.effects = effects;
        settings(settings);
    }

    @Override
    public void settings(FishingSettings settings) {
        levels = new LevelRule(settings.highestLevel());
        tree = new TreeRule(levels, FishingTree.shipped());
    }

    public LevelRule levels() {
        return levels;
    }

    public TreeRule tree() {
        return tree;
    }

    public Perks perks(UUID player) {
        return tree.perks(of(player));
    }

    public FisherProfile of(UUID player) {
        return store.of(player);
    }

    public int level(UUID player) {
        return levels.level(of(player).experience());
    }

    /** Records a catch of fish and its experience, and announces a level reached. */
    public void caught(Player player, String fishId, int amount, Tier tier, double kilos) {
        caught(player, fishId, amount, tier, kilos, null);
    }

    /** @param water where it was caught, for the tree's lava and void experience; null for anywhere */
    public void caught(Player player, String fishId, int amount, Tier tier, double kilos, Water water) {
        UUID id = player.getUniqueId();
        boolean first = of(id).caught(fishId) == 0;
        reward(player, profile -> {
                    FisherProfile changed = profile.withCatch(fishId, amount).withWeight(fishId, kilos);
                    return water == Water.VOID ? changed.withVoidCatch(tier, amount) : changed;
                },
                BONUS.experience((long) tier.experience() * amount, perks(id), water, first));
    }

    /** Staff granting fishing experience by hand. */
    public void grant(Player player, long experience) {
        reward(player, UnaryOperator.identity(), experience);
    }

    public void openedChest(Player player, Tier tier) {
        reward(player, profile -> profile.withChestOpened(tier),
                BONUS.chestExperience(tier.experience() * 2L, perks(player.getUniqueId())));
    }

    private void reward(Player player, UnaryOperator<FisherProfile> change, long experience) {
        UUID id = player.getUniqueId();
        int before = level(id);
        store.queue(id, profile -> change.apply(profile).withExperience(experience));
        int after = level(id);
        if (after > before) {
            back(player, () -> {
                effects.play(id, Cues.EARNED);
                messages.send(player, "fishing.level-up", "level", String.valueOf(after),
                        "points", String.valueOf(tree.left(of(id))));
            });
        }
    }

    /** Writes what catches and experience have queued; on its own thread, as it is a database write. */
    public boolean flush() {
        return store.flush();
    }

    public void flushSoon() {
        Scheduling.async(plugin, store::flush);
    }

    /**
     * Spends a point on a skill. {@code then} hears, on the player's thread, empty when it went through, else why
     * not.
     */
    /** A skin token used: the skin is theirs for good. Written off the world's threads; {@code then} on theirs. */
    public void unlockSkin(Player player, de.raindancer.modules.fishing.model.RodSkin skin, Consumer<Boolean> then) {
        UUID id = player.getUniqueId();
        Scheduling.async(plugin, () -> {
            boolean written = store.change(id, profile -> profile.withSkin(skin));
            Scheduling.entity(plugin, player, () -> then.accept(written));
        });
    }

    /**
     * Takes another rank of a skill tree node. {@code then} hears, on the player's thread, empty when it went
     * through, else why not.
     */
    public void learn(Player player, TreeNode node, Consumer<Optional<SkillTree.Refusal>> then) {
        UUID id = player.getUniqueId();
        TreeRule rule = tree;
        Optional<SkillTree.Refusal> refusal = rule.refusal(of(id), node);
        if (refusal.isPresent()) {
            then.accept(refusal);
            return;
        }
        Scheduling.async(plugin, () -> {
            boolean written = store.change(id, profile -> rule.refusal(profile, node).isPresent()
                    ? profile : profile.withNode(node.id(), profile.rank(node.id()) + 1));
            Optional<SkillTree.Refusal> outcome = written ? Optional.empty() : Optional.of(SkillTree.Refusal.NO_POINTS);
            back(player, () -> then.accept(outcome));
        });
    }

    /** Takes every skill point back for the fee; tells the player how it went, then runs {@code then}. */
    public void unlearnAll(Player player, String fee, Runnable then) {
        UUID id = player.getUniqueId();
        FisherProfile before = of(id);
        if (before.nodes().isEmpty()) {
            messages.send(player, "fishing.skills.nothing-to-unlearn");
            effects.play(id, Cues.NO);
            then.run();
            return;
        }
        // Once for everybody whose four old skills became the tree: their points are theirs to place anew.
        Money price = before.freeUnlearn() ? Fees.amount("0") : Fees.amount(fee);
        Scheduling.async(plugin, () -> {
            EconomyResult paid = Fees.charge(id, price, "Unlearning fishing skills", "fishing.skill-reset");
            if (!paid.succeeded()) {
                back(player, () -> {
                    messages.send(player, "fishing.skills.reset-not-paid", "price", Fees.format(price));
                    effects.play(id, Cues.NO);
                    then.run();
                });
                return;
            }
            if (!store.change(id, FisherProfile::withoutNodes)) {
                Fees.refund(id, paid.amount(), "Unlearning fishing skills", "fishing.skill-reset");
                back(player, () -> {
                    messages.send(player, "fishing.skills.reset-failed");
                    then.run();
                });
                return;
            }
            back(player, () -> {
                effects.play(id, Cues.OK);
                messages.send(player, "fishing.skills.reset", "points",
                        String.valueOf(tree.left(of(id))));
                then.run();
            });
        });
    }

    private void back(Player player, Runnable task) {
        if (player.isOnline()) {
            Scheduling.entity(plugin, player, task);
        }
    }
}
