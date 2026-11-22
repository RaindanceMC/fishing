package de.raindancer.modules.fishing.model;

import java.util.List;

/**
 * Fish that belong together: catch one of each and the duo is done, which earns an achievement and a title.
 *
 * @param title what the achievement is called
 * @param badge the title worn in front of the name, in MiniMessage
 */
public record Duo(String id, String title, String badge, String description, List<String> fish) {

    public Duo {
        fish = List.copyOf(fish);
        description = description == null ? "" : description;
    }

    /** Its achievement's id under fishing's ones. */
    public String achievement() {
        return "duo-" + id;
    }

    public boolean doneBy(FisherProfile profile) {
        return fish.stream().allMatch(each -> profile.caught(each) > 0);
    }
}
