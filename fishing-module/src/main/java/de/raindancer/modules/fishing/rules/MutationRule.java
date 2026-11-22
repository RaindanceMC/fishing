package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Mutation;

import java.util.Optional;
import java.util.Random;

/** Whether a fish comes up as a rare variant. */
public final class MutationRule implements IFishingRule {

    /** @param luck what the odds are multiplied by; 1 for none */
    public Optional<Mutation> roll(Random random, double luck) {
        if (luck <= 0) {
            return Optional.empty();
        }
        double draw = random.nextDouble();
        double edge = 0;
        Mutation[] all = Mutation.values();
        // rarest first, so luck large enough to fill the range still leaves the rare ones their share
        for (int n = all.length - 1; n >= 0; n--) {
            edge += all[n].chance() * luck;
            if (draw < edge) {
                return Optional.of(all[n]);
            }
        }
        return Optional.empty();
    }

    @Override
    public String describe() {
        return "whether a fish comes up albino, melanistic, golden or prismatic";
    }
}
