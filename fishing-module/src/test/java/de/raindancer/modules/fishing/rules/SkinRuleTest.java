package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.RodSkin;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkinRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final SkinRule rule = new SkinRule(CATALOG, new LevelRule(50));

    @Test
    @DisplayName("nobody new has a skin; a level skin comes with the level")
    void level() {
        assertThat(rule.unlocked(FisherProfile.NEW)).isEmpty();
        long tenth = new LevelRule(50).experienceFor(RodSkin.DRIFTWOOD.level());
        assertThat(rule.unlocked(FisherProfile.NEW.withExperience(tenth))).contains(RodSkin.DRIFTWOOD)
                .doesNotContain(RodSkin.GOLDEN);
    }

    @Test
    @DisplayName("a water's skin comes with every fish of that water caught, not one fewer")
    void water() {
        List<FishKind> frozen = CATALOG.fish().stream().filter(kind -> kind.livesIn(Water.FROZEN)).toList();
        assertThat(frozen).hasSizeGreaterThan(1);
        FisherProfile almost = FisherProfile.NEW;
        for (FishKind kind : frozen.subList(1, frozen.size())) {
            almost = almost.withCatch(kind.id(), 1);
        }
        assertThat(rule.unlocked(almost)).doesNotContain(RodSkin.FROSTBITE);
        assertThat(rule.unlocked(almost.withCatch(frozen.getFirst().id(), 1))).contains(RodSkin.FROSTBITE);
    }

    @Test
    @DisplayName("a chest skin only ever comes from its token, kept on the profile")
    void chest() {
        FisherProfile everything = FisherProfile.NEW.withExperience(Long.MAX_VALUE / 4);
        for (FishKind kind : CATALOG.fish()) {
            everything = everything.withCatch(kind.id(), 1);
        }
        assertThat(rule.unlocked(everything)).doesNotContain(RodSkin.STARFALL, RodSkin.ABYSSAL, RodSkin.BONEWHALE);
        assertThat(rule.unlocked(FisherProfile.NEW.withSkin(RodSkin.STARFALL))).containsExactly(RodSkin.STARFALL);
    }
}
