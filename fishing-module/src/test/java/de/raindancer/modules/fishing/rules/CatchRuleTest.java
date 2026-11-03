package de.raindancer.modules.fishing.rules;

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

class CatchRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final CatchRule rule = new CatchRule(CATALOG);

    private static CatchRule.Cast cast(Water water, Rod rod, FisherProfile profile, boolean spot) {
        return new CatchRule.Cast(water, rod, profile, spot, 25, 50, true);
    }

    private Map<Tier, Integer> chestTiers(CatchRule.Cast cast, int rolls) {
        Random random = new Random(7);
        Map<Tier, Integer> seen = new EnumMap<>(Tier.class);
        for (int n = 0; n < rolls; n++) {
            if (rule.roll(cast, random) instanceof Catch.Chest chest) {
                seen.merge(chest.tier(), 1, Integer::sum);
            }
        }
        return seen;
    }

    @Test
    @DisplayName("what bites in a water lives in that water")
    void fishComeFromTheirWater() {
        Random random = new Random(1);
        for (Water water : Water.values()) {
            Rod rod = Rod.PLAIN.with(RodPart.LINE, 2);
            for (int n = 0; n < 2000; n++) {
                if (rule.roll(cast(water, rod, FisherProfile.NEW, false), random) instanceof Catch.Fish fish) {
                    assertThat(fish.kind().livesIn(water)).as(fish.kind().id() + " in " + water).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("the void chest only ever comes out of the void")
    void voidChestOnlyInTheVoid() {
        Rod rod = Rod.PLAIN.with(RodPart.LINE, 2).with(RodPart.LURE, 3);
        for (Water water : Water.values()) {
            Map<Tier, Integer> seen = chestTiers(cast(water, rod, FisherProfile.NEW, true), 40000);
            if (water == Water.VOID) {
                assertThat(seen).containsKey(Tier.MYTHIC);
            } else {
                assertThat(seen).as(water.name()).doesNotContainKey(Tier.MYTHIC);
            }
        }
    }

    @Test
    @DisplayName("Leviathan chests are the ocean's (and the void's); a river never gives one")
    void chestTiersFollowTheWater() {
        Rod rod = Rod.PLAIN.with(RodPart.LURE, 3);
        assertThat(chestTiers(cast(Water.RIVER, rod, FisherProfile.NEW, true), 40000))
                .doesNotContainKeys(Tier.EPIC, Tier.LEGENDARY, Tier.MYTHIC).containsKey(Tier.COMMON);
        assertThat(chestTiers(cast(Water.OCEAN, rod, FisherProfile.NEW, true), 80000))
                .containsKey(Tier.LEGENDARY);
    }

    @Test
    @DisplayName("the void never gives anything below rare")
    void voidIsNeverCommon() {
        Random random = new Random(3);
        Rod rod = Rod.PLAIN.with(RodPart.LINE, 2);
        for (int n = 0; n < 5000; n++) {
            Catch caught = rule.roll(cast(Water.VOID, rod, FisherProfile.NEW, false), random);
            assertThat(caught.tier().atLeast(Tier.RARE)).as(caught.toString()).isTrue();
        }
    }

    @Test
    @DisplayName("a lure, treasure sense and a spot each make chests more likely")
    void chestChanceGrows() {
        assertThat(rule.chestPermille(cast(Water.RIVER, Rod.PLAIN, FisherProfile.NEW, false))).isEqualTo(25);
        assertThat(rule.chestPermille(cast(Water.RIVER, Rod.PLAIN.with(RodPart.LURE, 2), FisherProfile.NEW, false)))
                .isEqualTo(55);
        assertThat(rule.chestPermille(cast(Water.RIVER, Rod.PLAIN,
                FisherProfile.NEW.withNode("treasure_sense", 3), false))).isEqualTo(55);
        assertThat(rule.chestPermille(cast(Water.RIVER, Rod.PLAIN, FisherProfile.NEW, true))).isEqualTo(75);
    }

    @Test
    @DisplayName("rare instinct makes rarer fish come up more often")
    void rareInstinctShiftsUp() {
        int plain = rareOrBetter(FisherProfile.NEW);
        int instinct = rareOrBetter(FisherProfile.NEW.withNode("rare_instinct", 5));
        assertThat(instinct).isGreaterThan(plain);
    }

    private int rareOrBetter(FisherProfile profile) {
        Random random = new Random(11);
        int count = 0;
        for (int n = 0; n < 20000; n++) {
            Catch caught = rule.roll(cast(Water.OCEAN, Rod.PLAIN, profile, false), random);
            if (caught instanceof Catch.Fish && caught.tier().atLeast(Tier.RARE)) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("treasure and junk vanilla put on the hook stay vanilla's when no chest bites")
    void vanillaKeepsItsTreasure() {
        Random random = new Random(5);
        CatchRule.Cast notAFish = new CatchRule.Cast(Water.RIVER, Rod.PLAIN, FisherProfile.NEW, false, 0, 0, false);
        for (int n = 0; n < 200; n++) {
            assertThat(rule.roll(notAFish, random)).isInstanceOf(Catch.Vanilla.class);
        }
    }

    @Test
    @DisplayName("a reel and double catch sometimes bring two fish up")
    void doubleCatch() {
        Random random = new Random(9);
        Rod rod = Rod.PLAIN.with(RodPart.REEL, 3);
        FisherProfile profile = FisherProfile.NEW.withNode("double_catch", 5);
        int doubles = 0;
        for (int n = 0; n < 4000; n++) {
            if (rule.roll(cast(Water.RIVER, rod, profile, false), random) instanceof Catch.Fish fish
                    && fish.amount() == 2) {
                doubles++;
            }
        }
        // 150 + 200 permille of the fish that bite
        assertThat(doubles).isBetween(1100, 1700);
    }

    @Test
    @DisplayName("the tree's chest perks count where they belong: in a spot, in lava, over the void")
    void treeChestPerks() {
        FisherProfile spots = FisherProfile.NEW.withNode("spot_treasure", 3);
        assertThat(rule.chestPermille(cast(Water.OCEAN, Rod.PLAIN, spots, true)))
                .isEqualTo(rule.chestPermille(cast(Water.OCEAN, Rod.PLAIN, FisherProfile.NEW, true)) + 30);
        assertThat(rule.chestPermille(cast(Water.OCEAN, Rod.PLAIN, spots, false))).isEqualTo(25);
        FisherProfile ember = FisherProfile.NEW.withNode("ember_path", 1);
        assertThat(rule.chestPermille(cast(Water.LAVA, Rod.PLAIN, ember, false))).isEqualTo(25 + 15);
        assertThat(rule.chestPermille(cast(Water.VOID, Rod.PLAIN, ember, false))).isEqualTo(25);
    }

    private Map<Tier, Integer> chestTiers(FisherProfile profile, int casts) {
        Random random = new Random(31);
        Map<Tier, Integer> seen = new java.util.EnumMap<>(Tier.class);
        CatchRule.Cast cast = new CatchRule.Cast(Water.OCEAN, Rod.PLAIN, profile, false, 1000, 0, true);
        for (int n = 0; n < casts; n++) {
            if (rule.roll(cast, random) instanceof Catch.Chest chest) {
                seen.merge(chest.tier(), 1, Integer::sum);
            }
        }
        return seen;
    }

    @Test
    @DisplayName("Better Chests now and then lifts a chest a tier, never past its water's best")
    void chestsComeUpBetter() {
        Map<Tier, Integer> plain = chestTiers(FisherProfile.NEW, 20000);
        Map<Tier, Integer> better = chestTiers(FisherProfile.NEW.withNode("better_chests", 3)
                .withNode("krakens_hoard", 1), 20000);
        assertThat(better.getOrDefault(Tier.COMMON, 0)).isLessThan(plain.getOrDefault(Tier.COMMON, 0));
        assertThat(better.getOrDefault(Tier.LEGENDARY, 0)).isGreaterThan(plain.getOrDefault(Tier.LEGENDARY, 0));
        assertThat(better).doesNotContainKey(Tier.MYTHIC);
    }

    @Test
    @DisplayName("Legend Lure brings legendary and mythic catches up more often")
    void legendLure() {
        Map<Tier, Integer> plain = chestTiers(FisherProfile.NEW, 40000);
        Map<Tier, Integer> lured = chestTiers(FisherProfile.NEW.withNode("legend_seeker", 1), 40000);
        assertThat(lured.getOrDefault(Tier.LEGENDARY, 0)).isGreaterThan(plain.getOrDefault(Tier.LEGENDARY, 0) * 13 / 10);
    }
}
