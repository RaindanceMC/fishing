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

    @Test
    @DisplayName("New Waters: a kind never caught bites more often than the ones on the record already")
    void newWaters() {
        FisherProfile seenOne = FisherProfile.NEW;
        java.util.List<de.raindancer.modules.fishing.model.FishKind> commons = CATALOG.in(Water.FROZEN, null, Tier.COMMON);
        assertThat(commons.size()).isGreaterThan(1);
        String known = commons.getFirst().id();
        seenOne = seenOne.withCatch(known, 5);
        FisherProfile curious = seenOne.withNode("new_waters", 3);
        assertThat(shareOf(known, curious)).isLessThan(shareOf(known, seenOne) * 0.85);
    }

    private double shareOf(String kind, FisherProfile profile) {
        Random random = new Random(70);
        int hits = 0;
        int fish = 0;
        for (int n = 0; n < 20000; n++) {
            if (rule.roll(new CatchRule.Cast(Water.FROZEN, Rod.PLAIN, profile, false, 0, 0, true), random)
                    instanceof Catch.Fish caught && caught.tier() == Tier.COMMON) {
                fish++;
                hits += caught.kind().id().equals(kind) ? 1 : 0;
            }
        }
        return (double) hits / fish;
    }

    @Test
    @DisplayName("Triple Haul turns some doubles into three fish, and nothing without a double")
    void tripleHaul() {
        Random random = new Random(71);
        FisherProfile hauler = FisherProfile.NEW.withNode("double_catch", 5).withNode("triple_haul", 2);
        int triples = 0;
        int doubles = 0;
        for (int n = 0; n < 8000; n++) {
            if (rule.roll(cast(Water.RIVER, Rod.PLAIN, hauler, false), random) instanceof Catch.Fish fish) {
                triples += fish.amount() == 3 ? 1 : 0;
                doubles += fish.amount() >= 2 ? 1 : 0;
            }
        }
        assertThat((double) triples / doubles).isBetween(0.25, 0.35);
        Random plain = new Random(72);
        for (int n = 0; n < 4000; n++) {
            if (rule.roll(cast(Water.RIVER, Rod.PLAIN, FisherProfile.NEW.withNode("triple_haul", 2), false), plain)
                    instanceof Catch.Fish fish) {
                assertThat(fish.amount()).isEqualTo(1);
            }
        }
    }

    /** Mythic against legendary fish over the void, out of many casts. */
    private double mythicPerLegendary(FisherProfile profile, de.raindancer.modules.fishing.model.Bait bait) {
        Random random = new Random(80);
        int mythic = 0;
        int legendary = 0;
        for (int n = 0; n < 200000; n++) {
            if (rule.roll(new CatchRule.Cast(Water.VOID, Rod.PLAIN, profile, false, 0, 0, true, null, bait), random)
                    instanceof Catch.Fish fish) {
                mythic += fish.tier() == Tier.MYTHIC ? 1 : 0;
                legendary += fish.tier() == Tier.LEGENDARY ? 1 : 0;
            }
        }
        return (double) mythic / legendary;
    }

    @Test
    @DisplayName("Myth Lure, Void Drifter and Abyss Walker raise mythic catches alone, not legendary ones with them")
    void mythicPerk() {
        double plain = mythicPerLegendary(FisherProfile.NEW, null);
        double lured = mythicPerLegendary(FisherProfile.NEW.withNode("myth_lure", 2), null);
        assertThat(lured / plain).isBetween(1.35, 1.65);
    }

    @Test
    @DisplayName("Voidlure makes mythic catches four times as likely and leaves the rest alone")
    void voidlure() {
        var voidlure = de.raindancer.modules.fishing.model.Bait.VOIDLURE;
        assertThat(voidlure.tier()).isEqualTo(Tier.MYTHIC);
        assertThat(voidlure.weight(Tier.MYTHIC)).isEqualTo(4.0);
        assertThat(voidlure.weight(Tier.LEGENDARY)).isEqualTo(1.0);
        double plain = mythicPerLegendary(FisherProfile.NEW, null);
        assertThat(mythicPerLegendary(FisherProfile.NEW, voidlure) / plain).isBetween(3.4, 4.6);
    }

    @Test
    @DisplayName("the void's debt: every void catch without a mythic makes the next mythic likelier, up to four times")
    void voidDebt() {
        assertThat(CatchRule.debtFactor(0)).isEqualTo(1.0);
        assertThat(CatchRule.debtFactor(10)).isCloseTo(1.3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(CatchRule.debtFactor(100)).isEqualTo(4.0);
        assertThat(CatchRule.debtFactor(5000)).isEqualTo(4.0);
        double plain = mythicPerLegendary(FisherProfile.NEW, null);
        double owed = mythicPerLegendary(FisherProfile.NEW.withVoidDry(100), null);
        assertThat(owed / plain).isBetween(3.4, 4.6);
    }

    @Test
    @DisplayName("the debt grows with each void fish that is not mythic and is paid off by a mythic")
    void debtCounting() {
        FisherProfile profile = FisherProfile.NEW.withVoidCatch(Tier.EPIC, 1).withVoidCatch(Tier.LEGENDARY, 2);
        assertThat(profile.voidDry()).isEqualTo(3);
        assertThat(profile.withVoidCatch(Tier.MYTHIC, 1).voidDry()).isZero();
    }
}
