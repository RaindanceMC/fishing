package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Skill;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.rules.LevelRule;
import de.raindancer.modules.fishing.rules.SkillRule;
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
 * <p>Reading a profile answers from memory; every change goes to the database, so it runs off the server's threads
 * and comes back to the player's own thread to tell them how it went.
 */
public final class ProfileService implements IFishingService {

    private final Plugin plugin;
    private final ProfileStore store;
    private final Messages messages;
    private final Effects effects;
    private volatile LevelRule levels;
    private volatile SkillRule skills;

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
        skills = new SkillRule(levels);
    }

    public LevelRule levels() {
        return levels;
    }

    public SkillRule skills() {
        return skills;
    }

    public FisherProfile of(UUID player) {
        return store.of(player);
    }

    public int level(UUID player) {
        return levels.level(of(player).experience());
    }

    /** Records a catch of fish and its experience, and announces a level reached. */
    public void caught(Player player, String fishId, int amount, Tier tier, double kilos) {
        reward(player, profile -> profile.withCatch(fishId, amount).withWeight(fishId, kilos),
                (long) tier.experience() * amount);
    }

    /** Staff granting fishing experience by hand. */
    public void grant(Player player, long experience) {
        reward(player, UnaryOperator.identity(), experience);
    }

    public void openedChest(Player player, Tier tier) {
        reward(player, profile -> profile.withChestOpened(tier), tier.experience() * 2L);
    }

    private void reward(Player player, UnaryOperator<FisherProfile> change, long experience) {
        UUID id = player.getUniqueId();
        Scheduling.async(plugin, () -> {
            int before = level(id);
            store.change(id, profile -> change.apply(profile).withExperience(experience));
            int after = level(id);
            if (after > before) {
                back(player, () -> {
                    effects.play(id, Cues.EARNED);
                    messages.send(player, "fishing.level-up", "level", String.valueOf(after),
                            "points", String.valueOf(skills.pointsLeft(of(id))));
                });
            }
        });
    }

    /**
     * Spends a point on a skill. {@code then} hears, on the player's thread, empty when it went through, else why
     * not.
     */
    public void learn(Player player, Skill skill, Consumer<Optional<SkillRule.Refusal>> then) {
        UUID id = player.getUniqueId();
        Optional<SkillRule.Refusal> refusal = skills.refusal(of(id), skill);
        if (refusal.isPresent()) {
            then.accept(refusal);
            return;
        }
        Scheduling.async(plugin, () -> {
            boolean written = store.change(id, profile -> skills.refusal(profile, skill).isPresent()
                    ? profile : profile.withSkill(skill, profile.rank(skill) + 1));
            Optional<SkillRule.Refusal> outcome = written ? Optional.empty() : Optional.of(SkillRule.Refusal.NO_POINTS);
            back(player, () -> then.accept(outcome));
        });
    }

    /** Takes every skill point back for the fee; tells the player how it went, then runs {@code then}. */
    public void unlearnAll(Player player, String fee, Runnable then) {
        UUID id = player.getUniqueId();
        if (of(id).pointsSpent() == 0) {
            messages.send(player, "fishing.skills.nothing-to-unlearn");
            effects.play(id, Cues.NO);
            then.run();
            return;
        }
        Money price = Fees.amount(fee);
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
            if (!store.change(id, FisherProfile::withoutSkills)) {
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
                        String.valueOf(skills.pointsLeft(of(id))));
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
