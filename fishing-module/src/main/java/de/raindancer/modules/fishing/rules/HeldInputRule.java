package de.raindancer.modules.fishing.rules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swallows an input the fisher is still holding when a fight ends. A held button repeats (right click every 4 ticks,
 * shift in a boat every tick), so every swallowed repeat moves the window on: it ends only once the button was let go
 * for the whole window, and a fresh press after that goes through.
 */
public final class HeldInputRule implements IFishingRule {

    private final long windowMillis;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    public HeldInputRule(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** A fight just ended: what the fisher is holding now is left over from it. */
    public void arm(UUID player, long now) {
        until.put(player, now + windowMillis);
    }

    /** True when this input is a leftover and must not act. */
    public boolean blocks(UUID player, long now) {
        Long end = until.get(player);
        if (end == null) {
            return false;
        }
        if (now > end) {
            until.remove(player, end);
            return false;
        }
        until.put(player, now + windowMillis);
        return true;
    }

    public void forget(UUID player) {
        until.remove(player);
    }

    @Override
    public String describe() {
        return "a right click or shift still held when a fight ends does not cast again or leave the boat";
    }
}
