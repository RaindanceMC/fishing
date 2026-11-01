package de.raindancer.modules.fishing.rules;

import java.util.function.IntPredicate;

/** Where a catch breaks the surface: the top of the liquid the hook is in, and how deep under it the catch starts. */
public final class EmergeRule implements IFishingRule {

    /** How high a full block of water or lava stands, as the game draws it. */
    public static final double FULL_LIQUID = 0.9;
    /** How far under the surface a catch starts, given room. */
    public static final double DEPTH = 1.2;

    /**
     * @param hookBlockY the block the hook is in
     * @param liquidAt   whether the block at that height (same column) is the hook's liquid
     * @return the surface's height, or NaN when there is no liquid at or right under the hook
     */
    public double surface(int hookBlockY, IntPredicate liquidAt) {
        int top = top(hookBlockY, liquidAt);
        return top == Integer.MIN_VALUE ? Double.NaN : top + FULL_LIQUID;
    }

    /** How many blocks of liquid there are from the surface down, at most four. */
    public int blocksDeep(int hookBlockY, IntPredicate liquidAt) {
        int top = top(hookBlockY, liquidAt);
        if (top == Integer.MIN_VALUE) {
            return 0;
        }
        int deep = 0;
        while (deep < 4 && liquidAt.test(top - deep)) {
            deep++;
        }
        return deep;
    }

    /** How far under the surface to start in liquid this many blocks deep. */
    public double depth(int blocksDeep) {
        return Math.max(0.3, Math.min(DEPTH, blocksDeep * FULL_LIQUID - 0.3));
    }

    private static int top(int hookBlockY, IntPredicate liquidAt) {
        int y;
        if (liquidAt.test(hookBlockY)) {
            y = hookBlockY;
        } else if (liquidAt.test(hookBlockY - 1)) {
            y = hookBlockY - 1;
        } else {
            return Integer.MIN_VALUE;
        }
        while (liquidAt.test(y + 1)) {
            y++;
        }
        return y;
    }

    @Override
    public String describe() {
        return "where a catch breaks the surface and how deep under it it starts";
    }
}
