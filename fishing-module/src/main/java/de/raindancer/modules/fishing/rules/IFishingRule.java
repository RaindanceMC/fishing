package de.raindancer.modules.fishing.rules;

/** Decides and does nothing else: no saving, no sending, no scheduling, safe from any thread. */
public interface IFishingRule {

    String describe();
}
