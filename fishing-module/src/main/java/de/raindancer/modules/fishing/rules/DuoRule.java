package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Duo;
import de.raindancer.modules.fishing.model.FisherProfile;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Which duos a fisher has done, and which title they wear for it. */
public final class DuoRule implements IFishingRule {

    private final Catalog catalog;

    public DuoRule(Catalog catalog) {
        this.catalog = catalog;
    }

    public List<Duo> completed(FisherProfile profile) {
        return catalog.duos().stream().filter(duo -> duo.doneBy(profile)).toList();
    }

    /** The duos done once this catch is counted; only the duos the fish belongs to. */
    public List<Duo> finishedBy(FisherProfile profile, String fishId) {
        return catalog.duos().stream().filter(duo -> duo.fish().contains(fishId) && duo.doneBy(profile)).toList();
    }

    /** The first duo, in catalogue order, whose achievement {@code earned} holds. */
    public Optional<Duo> worn(Predicate<String> earned) {
        return catalog.duos().stream().filter(duo -> earned.test(duo.achievement())).findFirst();
    }

    @Override
    public String describe() {
        return "Duos: fish that belong together; one of each caught earns their title.";
    }
}
