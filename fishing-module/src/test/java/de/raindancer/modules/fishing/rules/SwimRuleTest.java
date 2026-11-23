package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.CatalogReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SwimRuleTest {

    private static final Catalog CATALOG = CatalogReader.shipped();
    private final SwimRule rule = new SwimRule();
    /** A tank three blocks long and one deep: water from x -0.4 to 2.4, y -0.15 to 0.3, z -0.4 to 0.4. */
    private final SwimRule.Water tank = SwimRule.Water.of(List.of(new SwimRule.V3(0, 0, 0), new SwimRule.V3(1, 0, 0),
            new SwimRule.V3(2, 0, 0)));

    private static FishKind kind(String id, Tier tier) {
        return new FishKind(id, id, tier, Set.of(Water.RIVER), false);
    }

    @Test
    @DisplayName("every fish has a temper; predators chase, rays and catfish keep to the sand, slim fish school")
    void tempers() {
        assertThat(rule.personality(CATALOG.fish("abyssal_angler").orElseThrow()).temper())
                .isEqualTo(SwimRule.Temper.AGGRESSIVE);
        assertThat(rule.personality(CATALOG.fish("bog_catfish").orElseThrow()).temper())
                .isEqualTo(SwimRule.Temper.BOTTOM);
        assertThat(rule.personality(CATALOG.fish("silver_herring").orElseThrow()).temper())
                .isEqualTo(SwimRule.Temper.SCHOOLING);
        assertThat(rule.personality(CATALOG.fish("hydro_dragonet").orElseThrow()).temper())
                .isEqualTo(SwimRule.Temper.AGGRESSIVE);
        assertThat(rule.personality(CATALOG.fish("raindancer_hoodfin").orElseThrow()).temper())
                .isEqualTo(SwimRule.Temper.PLAYFUL);
        for (FishKind kind : CATALOG.fish()) {
            SwimRule.Personality personality = rule.personality(kind);
            assertThat(personality.speed()).as(kind.id()).isBetween(0.05, 0.5);
            assertThat(personality.aggression()).as(kind.id()).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("the rarer a predator, the more aggressive; a calm fish is not aggressive at all")
    void aggressionByTier() {
        double common = rule.personality(kind("x", Tier.COMMON), SwimRule.Temper.AGGRESSIVE).aggression();
        double mythic = rule.personality(kind("x", Tier.MYTHIC), SwimRule.Temper.AGGRESSIVE).aggression();
        assertThat(mythic).isGreaterThan(common);
        assertThat(rule.personality(kind("x", Tier.MYTHIC), SwimRule.Temper.CALM).aggression()).isZero();
    }

    private SwimRule.Swimmer swimmer(int id, SwimRule.Temper temper, double x) {
        return new SwimRule.Swimmer(id, "kind" + id, rule.personality(kind("k" + id, Tier.LEGENDARY), temper),
                new SwimRule.V3(x, 0.05, 0));
    }

    @Test
    @DisplayName("nobody ever swims out of the water, whatever their temper")
    void staysInside() {
        Random random = new Random(1);
        List<SwimRule.Swimmer> fish = new ArrayList<>();
        int n = 0;
        for (SwimRule.Temper temper : SwimRule.Temper.values()) {
            fish.add(swimmer(n, temper, (n++ % 3)));
        }
        for (int step = 0; step < 2000; step++) {
            rule.step(fish, tank, random);
            for (SwimRule.Swimmer each : fish) {
                assertThat(tank.inside(each.at())).as(each.personality().temper() + " at " + each.at()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("an aggressive fish goes after another and nips it, and the one nipped flees")
    void chaseAndFlee() {
        Random random = new Random(2);
        SwimRule.Swimmer shark = swimmer(1, SwimRule.Temper.AGGRESSIVE, 0);
        SwimRule.Swimmer prey = swimmer(2, SwimRule.Temper.CALM, 2);
        List<SwimRule.Swimmer> fish = List.of(shark, prey);
        int nips = 0;
        boolean fled = false;
        for (int step = 0; step < 600; step++) {
            List<SwimRule.Nip> bites = rule.step(fish, tank, random);
            nips += bites.size();
            fled |= prey.fleeing();
        }
        assertThat(nips).as("it caught up at least once").isPositive();
        assertThat(fled).isTrue();
    }

    @Test
    @DisplayName("a calm fish never nips anyone")
    void calmIsPeaceful() {
        Random random = new Random(3);
        List<SwimRule.Swimmer> fish = List.of(swimmer(1, SwimRule.Temper.CALM, 0), swimmer(2, SwimRule.Temper.CALM, 1));
        for (int step = 0; step < 600; step++) {
            assertThat(rule.step(fish, tank, random)).isEmpty();
        }
    }

    @Test
    @DisplayName("a bottom dweller keeps low; a shy fish keeps its distance from the others")
    void bottomAndShy() {
        Random random = new Random(4);
        SwimRule.Swimmer catfish = swimmer(1, SwimRule.Temper.BOTTOM, 0);
        SwimRule.Swimmer shy = swimmer(2, SwimRule.Temper.SHY, 1);
        SwimRule.Swimmer playful = swimmer(3, SwimRule.Temper.PLAYFUL, 2);
        List<SwimRule.Swimmer> fish = List.of(catfish, shy, playful);
        double low = 0;
        double apart = 0;
        double near = 0;
        int steps = 3000;
        for (int step = 0; step < steps; step++) {
            rule.step(fish, tank, random);
            low += catfish.at().y();
            apart += Math.min(shy.at().distance(catfish.at()), shy.at().distance(playful.at()));
            near += Math.min(playful.at().distance(catfish.at()), playful.at().distance(shy.at()));
        }
        assertThat(low / steps).as("the catfish's mean height").isLessThan(-0.05);
        assertThat(apart / steps).as("the shy fish keeps further from the others than the playful one")
                .isGreaterThan(near / steps);
    }

    @Test
    @DisplayName("fish of a schooling kind gather; a lone fish just wanders")
    void schools() {
        Random random = new Random(5);
        SwimRule.Personality herring = rule.personality(kind("herring", Tier.COMMON), SwimRule.Temper.SCHOOLING);
        List<SwimRule.Swimmer> fish = List.of(new SwimRule.Swimmer(1, "herring", herring, new SwimRule.V3(0, 0, 0)),
                new SwimRule.Swimmer(2, "herring", herring, new SwimRule.V3(2, 0, 0)),
                new SwimRule.Swimmer(3, "herring", herring, new SwimRule.V3(1, 0, 0.3)));
        double spread = 0;
        int steps = 2000;
        for (int step = 0; step < steps; step++) {
            rule.step(fish, tank, random);
            if (step > 200) {
                spread += fish.get(0).at().distance(fish.get(1).at()) + fish.get(1).at().distance(fish.get(2).at());
            }
        }
        // three fish spread at random over a three-block tank sit about a block apart; a school much closer
        assertThat(spread / (steps - 201) / 2).isLessThan(0.75);
    }

    @Test
    @DisplayName("a schooling fish is not tied to its school: now and then it swims off on its own, then comes back")
    void schoolingFishGoOffAlone() {
        Random random = new Random(6);
        SwimRule.Personality herring = rule.personality(kind("herring", Tier.COMMON), SwimRule.Temper.SCHOOLING);
        SwimRule.Swimmer leader = new SwimRule.Swimmer(1, "herring", herring, new SwimRule.V3(0, 0, 0));
        SwimRule.Swimmer follower = new SwimRule.Swimmer(2, "herring", herring, new SwimRule.V3(0.1, 0, 0));
        List<SwimRule.Swimmer> fish = List.of(leader, follower);
        int alone = 0;
        int together = 0;
        for (int step = 0; step < 4000; step++) {
            rule.step(fish, tank, random);
            if (follower.alone()) {
                alone++;
            } else {
                together++;
            }
        }
        assertThat(alone).as("steps on its own").isPositive();
        assertThat(together).as("most of the time with the school").isGreaterThan(alone);
    }
}
