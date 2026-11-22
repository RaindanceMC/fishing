package de.raindancer.modules.fishing.service;

import de.raindancer.core.content.achievement.Achievement;
import de.raindancer.core.content.achievement.Achievements;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.identity.NameBadges;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Duo;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.rules.DuoRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Duos: one of each of their fish caught earns the duo's achievement, once, and its title in front of the name
 * (a wearable badge, so the cosmetics Tags page can take it off).
 */
public final class DuoService {

    /** Fixed rather than the plugin's name, so the same achievement counts in the standalone jar and the bundle. */
    static final String OWNER = "fishing";
    static final String BADGE = "fishing-duo";

    private final DuoRule rule;
    private final Catalog catalog;
    private final Achievements achievements;
    private final Messages messages;
    private final Effects effects;
    private final Function<UUID, FisherProfile> profiles;
    private final Map<String, Component> badges = new HashMap<>();

    public DuoService(Catalog catalog, Achievements achievements, Messages messages, Effects effects,
                      Function<UUID, FisherProfile> profiles) {
        this.rule = new DuoRule(catalog);
        this.catalog = catalog;
        this.achievements = achievements;
        this.messages = messages;
        this.effects = effects;
        this.profiles = profiles;
        catalog.duos().forEach(duo -> badges.put(duo.id(), MiniMessage.miniMessage().deserialize(duo.badge())));
    }

    /** Defines the achievements (an owner's edit of one stays) and puts the title badge up. */
    public void install() {
        if (achievements == null || catalog.duos().isEmpty()) {
            return;
        }
        for (Duo duo : catalog.duos()) {
            achievements.defineIfAbsent(Achievement.builder(OWNER, duo.achievement()).title(duo.title())
                    .description(duo.description()).icon(Material.TROPICAL_FISH).points(50).build());
        }
        NameBadges.register(BADGE, "Fishing duo title", this::badge);
    }

    public void uninstall() {
        NameBadges.unregister(BADGE);
    }

    /** After a catch is counted: tells the fisher about any duo this catch finished. */
    public void caught(Player player, String fishId) {
        if (achievements == null) {
            return;
        }
        UUID id = player.getUniqueId();
        for (Duo duo : rule.finishedBy(profiles.apply(id), fishId)) {
            if (achievements.award(id, key(duo))) {
                effects.play(id, Cues.EARNED);
                messages.send(player, "fishing.duo.complete", "title", duo.title());
            }
        }
    }

    /** Asked whenever the name is drawn, from any thread: a map lookup per duo, nothing more. */
    Optional<Component> badge(UUID player) {
        return rule.worn(achievement -> achievements.hasEarned(player, OWNER + ":" + achievement))
                .map(duo -> badges.get(duo.id()));
    }

    private static String key(Duo duo) {
        return OWNER + ":" + duo.achievement();
    }
}
