package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.Perks;

import java.util.Random;

/**
 * Auto-Reel and Auto-Rethrow, and the guard that keeps them from being an AFK farm: no rethrow for a fisher marked
 * away, and none after {@link #STILL_CASTS} rethrows in a row without the fisher turning their head. A water stream
 * can push a body about; only a player turns its view. A cast by hand starts the count again.
 */
public final class AutoRule implements IFishingRule {

    public static final int STILL_CASTS = 30;
    /** Degrees the view must turn, yaw or pitch, to count as somebody there. */
    static final float TURN = 5f;

    public enum Stop { NONE, AWAY, STILL }

    /** One fisher's run of rethrows. Not thread-safe: touched on the fisher's own thread only. */
    public static final class Watch {
        private int still;
        private float yaw = Float.NaN;
        private float pitch = Float.NaN;

        public void castByHand() {
            still = 0;
            yaw = Float.NaN;
        }
    }

    public boolean reels(Perks perks) {
        return perks.has(Perk.AUTO_REEL);
    }

    public boolean rethrows(Perks perks) {
        return perks.has(Perk.AUTO_RETHROW);
    }

    /**
     * Whether the line may go back out on its own.
     *
     * @param unguarded the fisher may auto-fish unwatched (staff, a permission)
     */
    public Stop rethrow(Watch watch, boolean away, boolean unguarded, float yaw, float pitch) {
        if (unguarded) {
            return Stop.NONE;
        }
        if (away) {
            return Stop.AWAY;
        }
        if (Float.isNaN(watch.yaw) || turned(watch.yaw, yaw) || Math.abs(watch.pitch - pitch) >= TURN) {
            watch.still = 0;
            watch.yaw = yaw;
            watch.pitch = pitch;
        }
        if (watch.still >= STILL_CASTS) {
            return Stop.STILL;
        }
        watch.still++;
        return Stop.NONE;
    }

    private static boolean turned(float from, float to) {
        float d = Math.abs(((to - from) % 360 + 540) % 360 - 180);
        return d >= TURN;
    }

    /** Ticks from the bite to the reel: a human moment, well inside vanilla's shortest nibble (20 ticks). */
    public int reelDelay(Random random) {
        return 4 + random.nextInt(9);
    }

    /** Ticks from the catch to the line going out again. */
    public int rethrowDelay(Random random) {
        return 15 + random.nextInt(16);
    }

    @Override
    public String describe() {
        return "auto-reel and auto-rethrow, and when the guard stops them";
    }
}
