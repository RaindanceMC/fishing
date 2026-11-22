package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Mutation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MutationRuleTest {

    private final MutationRule rule = new MutationRule();

    @Test
    @DisplayName("most fish are plain; over many catches each mutation turns up about as often as its chance says")
    void oddsHoldOverManyCatches() {
        Random random = new Random(7);
        Map<Mutation, Integer> seen = new EnumMap<>(Mutation.class);
        int plain = 0;
        int casts = 2_000_000;
        for (int n = 0; n < casts; n++) {
            Mutation got = rule.roll(random, 1.0).orElse(null);
            if (got == null) {
                plain++;
            } else {
                seen.merge(got, 1, Integer::sum);
            }
        }
        assertThat(plain).isGreaterThan((int) (casts * 0.98));
        for (Mutation mutation : Mutation.values()) {
            double expected = casts * mutation.chance();
            assertThat((double) seen.getOrDefault(mutation, 0)).as(mutation.name())
                    .isBetween(expected * 0.85, expected * 1.15);
        }
    }

    @Test
    @DisplayName("the rarer a mutation, the more it is worth")
    void rarerIsDearer() {
        Mutation[] all = Mutation.values();
        for (int n = 1; n < all.length; n++) {
            assertThat(all[n].chance()).isLessThanOrEqualTo(all[n - 1].chance());
            assertThat(all[n].priceFactor()).isGreaterThanOrEqualTo(all[n - 1].priceFactor());
        }
    }

    @Test
    @DisplayName("luck scales the odds, never past certainty, and no luck means no mutations")
    void luckScales() {
        Random random = new Random(3);
        int lucky = 0;
        for (int n = 0; n < 200_000; n++) {
            lucky += rule.roll(random, 4.0).isPresent() ? 1 : 0;
        }
        double base = java.util.Arrays.stream(Mutation.values()).mapToDouble(Mutation::chance).sum();
        assertThat((double) lucky).isBetween(4 * base * 200_000 * 0.85, 4 * base * 200_000 * 1.15);
        assertThat(rule.roll(new Random(1), 0).isPresent()).isFalse();
    }

    @Test
    @DisplayName("ids read back, and an unknown one is no mutation rather than an error")
    void parse() {
        for (Mutation mutation : Mutation.values()) {
            assertThat(Mutation.parse(mutation.id())).contains(mutation);
        }
        assertThat(Mutation.parse("sparkly")).isEmpty();
        assertThat(Mutation.parse(null)).isEmpty();
    }
}
