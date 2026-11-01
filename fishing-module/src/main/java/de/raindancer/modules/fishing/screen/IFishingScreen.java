package de.raindancer.modules.fishing.screen;

/** The menu grammar: greyed rather than hidden, every modifier said on its button, nothing irreversible unasked. */
public interface IFishingScreen {

    void open();

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
