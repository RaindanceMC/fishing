package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.TankPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Aquariums side by side or stacked are one big tank: which blocks belong together, and which sides of a block are joined to
 * another, which picks its model.
 */
public final class TankRule implements IFishingRule {

    /** Twelve blocks, three fish each: the 36 slots above a menu's toolbar. */
    public static final int MAX_TANKS = 12;

    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;
    public static final int UP = 16;
    public static final int DOWN = 32;
    /** How many looks a block can have, one per pattern of joined sides. */
    public static final int LOOKS = 64;

    private static final int[][] SIDES = {{0, 0, -1, NORTH}, {1, 0, 0, EAST}, {0, 0, 1, SOUTH}, {-1, 0, 0, WEST},
            {0, 1, 0, UP}, {0, -1, 0, DOWN}};

    /** The tank {@code start} is part of, nearest blocks first up to the cap, then in {@link TankPos#ORDER}. */
    public List<TankPos> group(TankPos start, Predicate<TankPos> isTank) {
        Set<TankPos> seen = new HashSet<>();
        Deque<TankPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        List<TankPos> found = new ArrayList<>();
        while (!queue.isEmpty() && found.size() < MAX_TANKS) {
            TankPos at = queue.poll();
            found.add(at);
            for (int[] side : SIDES) {
                TankPos next = at.plus(side[0], side[1], side[2]);
                if (seen.add(next) && isTank.test(next)) {
                    queue.add(next);
                }
            }
        }
        // A capped tank is the same twelve whichever block is asked from: start again from its first block.
        if (found.size() == MAX_TANKS) {
            TankPos first = found.stream().min(TankPos.ORDER).orElse(start);
            if (!first.equals(start)) {
                List<TankPos> again = group(first, isTank);
                if (again.contains(start)) {
                    return again;
                }
            }
        }
        found.sort(TankPos.ORDER);
        return found;
    }

    /** The sides of {@code at} joined to another block of its tank, as a bit mask. */
    public int look(TankPos at, Predicate<TankPos> inTank) {
        int mask = 0;
        for (int[] side : SIDES) {
            if (inTank.test(at.plus(side[0], side[1], side[2]))) {
                mask |= side[3];
            }
        }
        return mask;
    }

    @Override
    public String describe() {
        return "which aquariums side by side or stacked make one tank, and how each block of it looks";
    }
}
