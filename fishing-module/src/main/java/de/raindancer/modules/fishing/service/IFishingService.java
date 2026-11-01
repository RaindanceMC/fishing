package de.raindancer.modules.fishing.service;

import de.raindancer.modules.fishing.FishingSettings;

/** Does, and decides as little as possible: asks a rule. Holds a snapshot of the settings, swapped on change. */
public interface IFishingService {

    void settings(FishingSettings settings);
}
