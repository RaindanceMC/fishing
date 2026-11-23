package de.raindancer.modules.fishing.rules;

import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Tier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * How fish swim in an aquarium. Every kind has a temper — calm, shy, playful, schooling, a bottom dweller or
 * aggressive — and within it its own speed, restlessness and, for predators, aggression (more the rarer they are), so
 * no two kinds swim quite alike. One step moves every fish of a tank at once, so they react to each other: a predator
 * picks a fish of another kind, chases it and nips it, and the one nipped darts away; a shy fish keeps its distance;
 * a school follows its leader, though each of its fish now and then swims off on its own. Positions are in blocks, relative to the tank's first block; nothing leaves the water.
 */
public final class SwimRule implements IFishingRule {

    public enum Temper {
        CALM, SHY, PLAYFUL, SCHOOLING, BOTTOM, AGGRESSIVE;

        public String title() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        public static Temper parse(String text) {
            if (text == null) {
                return null;
            }
            for (Temper temper : values()) {
                if (temper.name().equalsIgnoreCase(text.trim())) {
                    return temper;
                }
            }
            return null;
        }
    }

    /**
     * @param speed        blocks per step at an easy pace
     * @param aggression   0 to 1: how readily it starts a chase
     * @param restlessness 0 to 1: how readily it sets off again after reaching where it was going
     * @param depth        0 the sand to 1 the surface, where it likes to be; negative for anywhere
     */
    public record Personality(Temper temper, double speed, double aggression, double restlessness, double depth) {
    }

    /** A point, or a direction, in blocks. */
    public record V3(double x, double y, double z) {

        V3 plus(V3 other) {
            return new V3(x + other.x, y + other.y, z + other.z);
        }

        V3 minus(V3 other) {
            return new V3(x - other.x, y - other.y, z - other.z);
        }

        V3 times(double factor) {
            return new V3(x * factor, y * factor, z * factor);
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public double distance(V3 other) {
            return minus(other).length();
        }
    }

    /** One fish swimming; changed by every step. */
    public static final class Swimmer {
        private final int id;
        private final String kind;
        private final Personality personality;
        private V3 at;
        private V3 goal;
        private V3 heading = new V3(-1, 0, 0);
        private int fleeing;
        private V3 fleeFrom;
        private Integer chasing;
        private int chaseSteps;
        private int rest;
        private int alone;
        private boolean moved;

        public Swimmer(int id, String kind, Personality personality, V3 at) {
            this.id = id;
            this.kind = kind;
            this.personality = personality;
            this.at = at;
        }

        public int id() {
            return id;
        }

        public Personality personality() {
            return personality;
        }

        public V3 at() {
            return at;
        }

        /** The way it last moved, for turning its model. */
        public V3 heading() {
            return heading;
        }

        public boolean fleeing() {
            return fleeing > 0;
        }

        /** Whether the last step moved it; a resting fish needs nothing sent. */
        public boolean moved() {
            return moved;
        }

        /** A schooling fish off on its own for a while. */
        public boolean alone() {
            return alone > 0;
        }
    }

    /** A predator caught up with another fish. */
    public record Nip(int chaser, int victim, V3 at) {
    }

    /**
     * The water of a tank: one box per block, reaching to the block's edge where another block of the tank goes on.
     * Margins keep a fish's body clear of the glass, the sand and the surface.
     */
    public static final class Water {
        static final double SIDE = 0.32;
        static final double BOTTOM = -0.2;
        static final double TOP = 0.25;

        private final List<double[]> boxes = new ArrayList<>();
        private final List<V3> blocks;

        private Water(List<V3> blocks) {
            this.blocks = List.copyOf(blocks);
            Set<V3> all = new HashSet<>(blocks);
            for (V3 block : blocks) {
                double west = all.contains(block.plus(new V3(-1, 0, 0))) ? 0.5 : SIDE;
                double east = all.contains(block.plus(new V3(1, 0, 0))) ? 0.5 : SIDE;
                double north = all.contains(block.plus(new V3(0, 0, -1))) ? 0.5 : SIDE;
                double south = all.contains(block.plus(new V3(0, 0, 1))) ? 0.5 : SIDE;
                boxes.add(new double[]{block.x - west, block.y + BOTTOM, block.z - north,
                        block.x + east, block.y + TOP, block.z + south});
            }
        }

        /** @param blocks the tank's blocks, relative to its first */
        public static Water of(List<V3> blocks) {
            return new Water(blocks);
        }

        public boolean inside(V3 p) {
            for (double[] box : boxes) {
                if (p.x >= box[0] - 1e-9 && p.x <= box[3] + 1e-9 && p.y >= box[1] - 1e-9 && p.y <= box[4] + 1e-9
                        && p.z >= box[2] - 1e-9 && p.z <= box[5] + 1e-9) {
                    return true;
                }
            }
            return false;
        }

        /** A point somewhere in the water; at {@code depth} (0 sand, 1 surface) when that is not negative. */
        V3 anywhere(Random random, double depth) {
            double[] box = boxes.get(random.nextInt(boxes.size()));
            return within(box, random, depth);
        }

        /** A point in the box holding {@code near}, or one beside it. */
        V3 near(V3 near, Random random, double depth) {
            List<double[]> close = new ArrayList<>();
            for (int n = 0; n < boxes.size(); n++) {
                if (blocks.get(n).distance(new V3(Math.round(near.x), blocks.get(n).y, Math.round(near.z))) <= 1.01) {
                    close.add(boxes.get(n));
                }
            }
            return close.isEmpty() ? anywhere(random, depth) : within(close.get(random.nextInt(close.size())), random,
                    depth);
        }

        private static V3 within(double[] box, Random random, double depth) {
            double y = depth < 0 ? box[1] + random.nextDouble() * (box[4] - box[1])
                    : Math.max(box[1], Math.min(box[4], box[1] + depth * (box[4] - box[1])
                    + (random.nextDouble() - 0.5) * 0.06));
            return new V3(box[0] + random.nextDouble() * (box[3] - box[0]), y,
                    box[2] + random.nextDouble() * (box[5] - box[2]));
        }

        /** Where a move from {@code from} towards {@code to} may go: all the way, sliding along a wall, or nowhere. */
        V3 move(V3 from, V3 to) {
            if (inside(to)) {
                return to;
            }
            V3[] slides = {new V3(to.x, from.y, from.z), new V3(from.x, to.y, from.z), new V3(from.x, from.y, to.z),
                    new V3(to.x, to.y, from.z), new V3(to.x, from.y, to.z), new V3(from.x, to.y, to.z)};
            V3 best = from;
            double moved = 0;
            for (V3 slide : slides) {
                double distance = slide.distance(from);
                if (distance > moved && inside(slide)) {
                    best = slide;
                    moved = distance;
                }
            }
            return best;
        }
    }

    static final double NIP_REACH = 0.18;
    static final int FLEE_STEPS = 12;
    static final int CHASE_GIVE_UP = 40;
    static final int AFTER_NIP_REST = 20;
    /** Per step, the chance a schooling fish sets off on its own. */
    static final double GO_ALONE = 0.01;

    /** The kind's temper (the catalogue's, or what its shape suggests) with its own variation. */
    public Personality personality(FishKind kind) {
        Temper temper = Temper.parse(kind.temper());
        if (temper == null) {
            temper = byShape(kind.shape());
        }
        return personality(kind, temper);
    }

    public Personality personality(FishKind kind, Temper temper) {
        // a kind's own touch: the same every time for the same kind, different from its neighbours
        double touch = 0.85 + (Math.floorMod(kind.id().hashCode(), 31) / 30.0) * 0.3;
        int rank = kind.tier().ordinal();
        return switch (temper) {
            case CALM -> new Personality(temper, 0.08 * touch, 0, 0.3 * touch, -1);
            case SHY -> new Personality(temper, 0.07 * touch, 0, 0.2 * touch, 0.25);
            case PLAYFUL -> new Personality(temper, 0.2 * touch, 0, Math.min(1, 0.85 * touch), -1);
            case SCHOOLING -> new Personality(temper, 0.14 * touch, 0, 0.6 * touch, 0.6);
            case BOTTOM -> new Personality(temper, 0.06 * touch, 0, 0.25 * touch, 0);
            case AGGRESSIVE -> new Personality(temper, (0.15 + 0.02 * rank) * touch,
                    Math.min(1, (0.25 + 0.14 * rank) * touch), 0.6, -1);
        };
    }

    private static Temper byShape(String shape) {
        if (shape == null) {
            return Temper.CALM;
        }
        return switch (shape) {
            case "angler", "serpent", "eel", "marlin", "leviathan", "long" -> Temper.AGGRESSIVE;
            case "catfish", "ray" -> Temper.BOTTOM;
            case "slim" -> Temper.SCHOOLING;
            case "stubby" -> Temper.PLAYFUL;
            default -> Temper.CALM;
        };
    }

    /** Moves every fish one step; the nips that happened. */
    public List<Nip> step(List<Swimmer> fish, Water water, Random random) {
        List<Nip> nips = new ArrayList<>();
        for (Swimmer each : fish) {
            each.moved = false;
            Personality p = each.personality;
            double pace = p.speed();
            if (each.rest > 0) {
                each.rest--;
            }
            if (each.fleeing > 0) {
                each.fleeing--;
                V3 away = each.at.minus(each.fleeFrom);
                double length = away.length();
                V3 dash = length < 1e-6 ? water.anywhere(random, -1)
                        : each.at.plus(away.times(0.8 / length));
                each.goal = water.inside(dash) ? dash : water.near(each.at, random, -1);
                pace *= 1.8;
            } else if (p.temper() == Temper.AGGRESSIVE && hunt(each, fish, random)) {
                Swimmer prey = byId(fish, each.chasing);
                each.goal = prey.at;
                pace *= 1.3;
                if (each.at.distance(prey.at) < NIP_REACH) {
                    nips.add(new Nip(each.id, prey.id, prey.at));
                    prey.fleeing = FLEE_STEPS;
                    prey.fleeFrom = each.at;
                    each.chasing = null;
                    each.rest = AFTER_NIP_REST;
                }
            } else if (p.temper() == Temper.SHY && nearestOther(each, fish) != null
                    && each.at.distance(nearestOther(each, fish).at) < 0.6) {
                V3 away = each.at.minus(nearestOther(each, fish).at);
                V3 retreat = each.at.plus(away.times(0.5 / Math.max(1e-6, away.length())));
                each.goal = water.inside(retreat) ? retreat : water.anywhere(random, p.depth());
            } else if (p.temper() == Temper.SCHOOLING && follow(each, fish, random)) {
                // goal set by follow
            } else {
                wander(each, water, random);
            }
            if (each.goal == null) {
                continue;
            }
            V3 way = each.goal.minus(each.at);
            double distance = way.length();
            if (distance < 1e-6) {
                continue;
            }
            V3 next = water.move(each.at, each.at.plus(way.times(Math.min(distance, pace) / distance)));
            V3 moved = next.minus(each.at);
            if (moved.length() > 1e-6) {
                each.heading = moved;
                each.moved = true;
            }
            each.at = next;
        }
        return nips;
    }

    /** Whether it is chasing a fish now: carrying on, giving up, or picking a new one. */
    private static boolean hunt(Swimmer hunter, List<Swimmer> fish, Random random) {
        if (hunter.chasing != null) {
            if (++hunter.chaseSteps > CHASE_GIVE_UP || byId(fish, hunter.chasing) == null) {
                hunter.chasing = null;
                hunter.rest = AFTER_NIP_REST;
                return false;
            }
            return true;
        }
        if (hunter.rest > 0 || random.nextDouble() >= hunter.personality.aggression() * 0.08) {
            return false;
        }
        Swimmer prey = null;
        double best = Double.MAX_VALUE;
        for (Swimmer other : fish) {
            // never its own kind: a school of predators does not eat itself
            if (other != hunter && !other.kind.equals(hunter.kind) && other.at.distance(hunter.at) < best) {
                best = other.at.distance(hunter.at);
                prey = other;
            }
        }
        if (prey == null) {
            return false;
        }
        hunter.chasing = prey.id;
        hunter.chaseSteps = 0;
        return true;
    }

    /**
     * A school follows the first of its kind; the leader itself wanders. A follower is not tied to it: now and then it
     * swims off on its own for a few seconds and then finds the school again.
     */
    private static boolean follow(Swimmer each, List<Swimmer> fish, Random random) {
        if (each.alone > 0) {
            each.alone--;
            return false;
        }
        if (random.nextDouble() < GO_ALONE) {
            each.alone = 15 + random.nextInt(31);
            return false;
        }
        Swimmer leader = null;
        for (Swimmer other : fish) {
            if (other.kind.equals(each.kind) && (leader == null || other.id < leader.id)) {
                leader = other;
            }
        }
        if (leader == null || leader == each) {
            return false;
        }
        V3 offset = new V3((random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.3);
        each.goal = leader.at.plus(offset);
        return true;
    }

    private static void wander(Swimmer each, Water water, Random random) {
        boolean arrived = each.goal == null || each.goal.distance(each.at) < 0.05;
        if (arrived && random.nextDouble() < each.personality.restlessness() * 0.25) {
            each.goal = random.nextDouble() < 0.7 ? water.near(each.at, random, each.personality.depth())
                    : water.anywhere(random, each.personality.depth());
        } else if (arrived) {
            each.goal = null;
        }
    }

    private static Swimmer nearestOther(Swimmer each, List<Swimmer> fish) {
        Swimmer nearest = null;
        for (Swimmer other : fish) {
            if (other != each && (nearest == null || other.at.distance(each.at) < nearest.at.distance(each.at))) {
                nearest = other;
            }
        }
        return nearest;
    }

    private static Swimmer byId(List<Swimmer> fish, Integer id) {
        if (id == null) {
            return null;
        }
        for (Swimmer each : fish) {
            if (each.id == id) {
                return each;
            }
        }
        return null;
    }

    @Override
    public String describe() {
        return "how fish swim in an aquarium: each kind's temper, chases, nips, schools and shy ones keeping apart";
    }
}
