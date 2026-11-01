package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SpotAndBaitRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final CatchRule rule = new CatchRule(CATALOG);
    private final Rod rod = Rod.PLAIN.with(RodPart.LINE, 2).with(RodPart.LURE, 3);

    private static CatchRule.Cast cast(Water water, Rod rod, Tier spot, Bait bait) {
        return new CatchRule.Cast(water, rod, FisherProfile.NEW, spot != null, 25, 50, true, spot, bait);
    }

    private Map<Tier, Integer> chests(CatchRule.Cast cast) {
        Random random = new Random(3);
        Map<Tier, Integer> seen = new EnumMap<>(Tier.class);
        for (int n = 0; n < 60000; n++) {
            if (rule.roll(cast, random) instanceof Catch.Chest chest) {
                seen.merge(chest.tier(), 1, Integer::sum);
            }
        }
        return seen;
    }

    private int fishAtLeast(CatchRule.Cast cast, Tier floor) {
        Random random = new Random(5);
        int count = 0;
        for (int n = 0; n < 30000; n++) {
            if (rule.roll(cast, random) instanceof Catch.Fish fish && fish.kind().tier().atLeast(floor)) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("a fixed legendary spot on a river gives up chests a river never would — but never a void chest")
    void legendaryRiverSpot() {
        Map<Tier, Integer> seen = chests(cast(Water.RIVER, rod, Tier.LEGENDARY, null));
        assertThat(seen).containsKey(Tier.LEGENDARY).doesNotContainKey(Tier.MYTHIC);
        assertThat(chests(cast(Water.OCEAN, rod, Tier.MYTHIC, null))).as("mythic outside the void is legendary")
                .doesNotContainKey(Tier.MYTHIC).containsKey(Tier.LEGENDARY);
        assertThat(chests(cast(Water.VOID, rod, Tier.MYTHIC, null))).containsKey(Tier.MYTHIC);
    }

    @Test
    @DisplayName("a fixed common spot gives up nothing better than common, chest or fish")
    void commonSpot() {
        CatchRule.Cast cast = cast(Water.OCEAN, rod, Tier.COMMON, null);
        assertThat(chests(cast).keySet()).containsOnly(Tier.COMMON);
        assertThat(fishAtLeast(cast, Tier.UNCOMMON)).isZero();
    }

    @Test
    @DisplayName("gilded bait makes sea chests more likely, abyss bait epic fish")
    void baits() {
        assertThat(rule.chestPermille(cast(Water.OCEAN, rod, null, Bait.GILDED)))
                .isEqualTo(rule.chestPermille(cast(Water.OCEAN, rod, null, null)) + Bait.GILDED.chestPermille());
        int plain = fishAtLeast(cast(Water.OCEAN, Rod.PLAIN, null, null), Tier.EPIC);
        int baited = fishAtLeast(cast(Water.OCEAN, Rod.PLAIN, null, Bait.ABYSS), Tier.EPIC);
        assertThat(baited).isGreaterThan(plain * 3 / 2);
    }

    @Test
    @DisplayName("wriggler bait shortens the wait, never below the fastest a bite can be")
    void wriggler() {
        BiteRule bites = new BiteRule();
        double plain = bites.waitFactor(Rod.PLAIN, FisherProfile.NEW, false);
        assertThat(bites.waitFactor(Rod.PLAIN, FisherProfile.NEW, false, Bait.WRIGGLER))
                .isEqualTo(plain * Bait.WRIGGLER.waitFactor());
        Rod quickest = Rod.PLAIN.with(RodPart.HOOK, 5);
        assertThat(bites.waitFactor(quickest, FisherProfile.NEW, true, Bait.WRIGGLER))
                .isGreaterThanOrEqualTo(BiteRule.FASTEST);
    }
}
