package dev.sixik.aeupgrade.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * A node in the built-in development tree.
 *
 * @param id           stable index used by the wire protocol and saves
 * @param key          suffix of the localized name and effect keys
 * @param cost         cost in development points
 * @param amount       original fixed modifier used only to migrate old config files
 * @param sector       themed region of the map
 * @param route        exclusive route in the sector, or null for a shared starter
 * @param previous     ID of the prerequisite, {@link #ROOT} for a starter, or {@link #DISCONNECTED}
 * @param alternative  ID of a second acceptable prerequisite, or {@link #ROOT} if absent
 * @param x            horizontal map position in world units
 * @param y            vertical map position in world units
 * @param kind         visual tier of the node
 * @param requirements explicit prerequisites and their threshold, or null for a legacy link
 * @param linkRoutes   custom routes for incoming links, or null when all links are straight
 */
public record UpgradeNode(int id, String key, int cost, int amount, Sector sector, @Nullable Route route,
                          int previous, int alternative, float x, float y, Kind kind,
                          @Nullable Requirements requirements,
                          @Nullable List<UpgradeLinkRoute> linkRoutes) {
    /**
     * Sentinel for the ME core and for an absent alternate prerequisite.
     */
    public static final int ROOT = -1;
    /**
     * A node drawn without an incoming edge and locked until it is connected.
     */
    public static final int DISCONNECTED = -2;
    /**
     * Horizontal stretch of the radial layout for the widescreen map.
     */
    public static final float HORIZONTAL_STRETCH = 1.65f;

    /**
     * Radius of the shared sector starters in map units.
     */
    private static final int START_RADIUS = 66;
    /**
     * Radial placement of a route's eight nodes, in map units.
     */
    private static final int[] RADIAL = {116, 158, 202, 205, 252, 303, 299, 352};
    /**
     * Perpendicular distance from the sector axis, mirrored for the other route.
     */
    private static final int[] LATERAL = {32, 54, 36, 80, 57, 42, 88, 69};

    /**
     * Increases ordinary and dense cable channel capacity.
     */
    public static final UpgradeNode CHANNELS = starter(0, "channels", 4, Sector.NETWORK);
    /**
     * Adds pattern slots to the providers on the grid.
     */
    public static final UpgradeNode PATTERNS = routeNode(1, "patterns", 2, 9, Route.PATTERN_LIBRARY, 0, CHANNELS.id, ROOT);
    /**
     * Adds twenty percentage points to assembler crafting speed.
     */
    public static final UpgradeNode SPEED = starter(2, "speed", 20, Sector.CRAFTING);
    /**
     * Adds three recipe operations to the maximum assembly batch size.
     */
    public static final UpgradeNode BATCH = routeNode(3, "batch", 2, 3, Route.MASS_PRODUCTION, 0, SPEED.id, ROOT);
    /**
     * Reduces the idle draw of configurable devices by fifteen percentage points.
     */
    public static final UpgradeNode IDLE = starter(4, "idle", -15, Sector.POWER);
    /**
     * Reduces assembler crafting draw by twenty-five percentage points.
     */
    public static final UpgradeNode CRAFT_ENERGY = routeNode(5, "craft_energy", 2, -25, Route.EFFICIENCY, 0, IDLE.id, ROOT);
    /**
     * Raises the total tree speed bonus to fifty percentage points and caps assemblers.
     */
    public static final UpgradeNode OVERDRIVE = routeNode(6, "overdrive", 2, 50, Route.OVERCLOCKING, 0, SPEED.id, ROOT);

    /**
     * Number of stable IDs used by the original integer-mask save format.
     */
    static final int LEGACY_COUNT = 7;
    /**
     * The entire catalog in persistence and prerequisite order. Keep assigned indices when adding nodes.
     */
    private static final List<UpgradeNode> NODES = build();

    public UpgradeNode {
        linkRoutes = linkRoutes == null || linkRoutes.isEmpty() ? null : List.copyOf(linkRoutes);
        if (requirements != null) {
            previous = DISCONNECTED;
            alternative = ROOT;
        }
    }

    public UpgradeNode(int id, String key, int cost, int amount, Sector sector, @Nullable Route route,
                       int previous, int alternative, float x, float y, Kind kind) {
        this(id, key, cost, amount, sector, route, previous, alternative, x, y, kind, null, List.of());
    }

    public UpgradeNode(int id, String key, int cost, int amount, Sector sector, @Nullable Route route,
                       int previous, int alternative, float x, float y, Kind kind, @Nullable Requirements requirements) {
        this(id, key, cost, amount, sector, route, previous, alternative, x, y, kind, requirements, List.of());
    }

    /**
     * @param nodes         distinct incoming node IDs
     * @param count         number of those nodes that must be purchased
     * @param lockRemaining whether reaching the threshold closes the unpurchased inputs
     */
    public record Requirements(List<Integer> nodes, int count, @Nullable Boolean lockRemaining) {
        public Requirements {
            if (nodes == null || nodes.size() >= UpgradeCatalog.MAX_NODES || count < 1 || count > nodes.size()
                    || nodes.stream().anyMatch(id -> id == null || id < 0)
                    || new HashSet<>(nodes).size() != nodes.size()) {
                throw new IllegalArgumentException("Invalid prerequisite IDs or threshold.");
            }
            nodes = List.copyOf(nodes);
            lockRemaining = Boolean.TRUE.equals(lockRemaining) ? Boolean.TRUE : null;
        }

        public Requirements(List<Integer> nodes, int count) {
            this(nodes, count, null);
        }
    }

    /**
     * @return whether this node connects directly to the central ME core
     */
    public boolean connectsToCore() {
        return previous == ROOT;
    }

    /**
     * @return whether this node has no links to the core or other nodes
     */
    public boolean disconnected() {
        return requirements == null && previous == DISCONNECTED;
    }

    /**
     * @return number of incoming edges from other nodes
     */
    public int prerequisiteCount() {
        return requirements != null ? requirements.nodes().size() : previous < 0 ? 0 : alternative < 0 ? 1 : 2;
    }

    /**
     * @param index position in the incoming edge list
     * @return node ID at that position
     */
    public int prerequisiteId(int index) {
        return requirements != null ? requirements.nodes().get(index) : index == 0 ? previous : alternative;
    }

    /**
     * @return incoming node IDs, excluding the ME core
     */
    public List<Integer> prerequisiteIds() {
        if (requirements != null) {
            return requirements.nodes();
        }
        return switch (prerequisiteCount()) {
            case 0 -> List.of();
            case 1 -> List.of(previous);
            default -> List.of(previous, alternative);
        };
    }

    /**
     * @return how many incoming prerequisites must be purchased
     */
    public int requiredCount() {
        return requirements != null ? requirements.count() : previous >= 0 ? 1 : 0;
    }

    /**
     * @return whether buying the required number of inputs closes the remaining branches
     */
    public boolean locksRemaining() {
        return requirements != null && Boolean.TRUE.equals(requirements.lockRemaining());
    }

    /**
     * @param source prerequisite ID or the core's sentinel
     * @return whether an incoming edge from this source exists
     */
    public boolean connectedFrom(int source) {
        return source == ROOT ? connectsToCore() : source >= 0 && prerequisiteIds().contains(source);
    }

    /**
     * @return custom incoming routes in document order
     */
    public List<UpgradeLinkRoute> routes() {
        return linkRoutes == null ? List.of() : linkRoutes;
    }

    /**
     * @param source prerequisite ID or the core's sentinel
     * @return edited route, or null for the default straight line
     */
    public @Nullable UpgradeLinkRoute linkRoute(int source) {
        if (linkRoutes != null) {
            for (int i = 0; i < linkRoutes.size(); i++) {
                var link = linkRoutes.get(i);
                if (link.source() == source) {
                    return link;
                }
            }
        }
        return null;
    }

    /**
     * @param source      existing incoming link
     * @param replacement edited route, or null to reset it
     * @return a node with the updated route
     */
    public UpgradeNode withLinkRoute(int source, @Nullable UpgradeLinkRoute replacement) {
        if (!connectedFrom(source) || replacement != null && source != replacement.source()) {
            throw new IllegalArgumentException("Link route does not match an incoming edge.");
        }
        var routes = new ArrayList<>(routes());
        routes.removeIf(link -> link.source() == source);
        if (replacement != null && (replacement.style() != UpgradeLinkRoute.Style.POLYLINE
                || !replacement.points().isEmpty())) {
            routes.add(replacement);
        }
        routes.sort(Comparator.comparingInt(UpgradeLinkRoute::source));
        return new UpgradeNode(id, key, cost, amount, sector, route, previous, alternative, x, y, kind,
                requirements, routes);
    }

    /**
     * @param ids   incoming node IDs, or a single {@link #ROOT} to link the core
     * @param count minimum purchases among incoming IDs, or zero for empty links or the core
     * @return this node with the new links and threshold
     */
    public UpgradeNode withPrerequisites(List<Integer> ids, int count) {
        return withPrerequisites(ids, count, count > 0 && locksRemaining());
    }

    /**
     * @param ids           incoming node IDs, or a single {@link #ROOT} to link the core
     * @param count         minimum purchases among incoming IDs, or zero for empty links or the core
     * @param lockRemaining close the unused inputs when the count is reached
     * @return this node with the new prerequisite rule
     */
    public UpgradeNode withPrerequisites(List<Integer> ids, int count, boolean lockRemaining) {
        var routes = linkRoutes == null ? null : linkRoutes.stream().filter(link -> ids.contains(link.source())).toList();
        if (ids.isEmpty() || ids.equals(List.of(ROOT))) {
            if (count != 0 || lockRemaining) {
                throw new IllegalArgumentException("A threshold requires incoming nodes.");
            }
            return new UpgradeNode(id, key, cost, amount, sector, route, ids.isEmpty() ? DISCONNECTED : ROOT,
                    ROOT, x, y, kind, null, routes);
        }
        var threshold = new Requirements(ids, count, lockRemaining);
        if (!lockRemaining && count == 1 && ids.size() <= 2) {
            return new UpgradeNode(id, key, cost, amount, sector, route, ids.getFirst(),
                    ids.size() == 2 ? ids.getLast() : ROOT, x, y, kind, null, routes);
        }
        return new UpgradeNode(id, key, cost, amount, sector, route, DISCONNECTED, ROOT, x, y, kind, threshold, routes);
    }

    /**
     * @param replacementCost price in points
     * @param newX            horizontal map position
     * @param newY            vertical map position
     * @return node preserving its prerequisite rule
     */
    public UpgradeNode withCostAndPosition(int replacementCost, float newX, float newY) {
        return new UpgradeNode(id, key, replacementCost, amount, sector, route, previous, alternative, newX, newY, kind,
                requirements, linkRoutes);
    }

    /**
     * Themed regions arranged around the ME core.
     */
    public enum Sector {
        /**
         * Cable and pattern distribution.
         */
        NETWORK(-150),
        /**
         * Recipe execution.
         */
        CRAFTING(-30),
        /**
         * Energy efficiency and reserves.
         */
        POWER(90),
        /**
         * Item and fluid storage.
         */
        STORAGE(-90),
        /**
         * Automation and item transfers.
         */
        LOGISTICS(30),
        /**
         * Availability and diagnostics.
         */
        RESILIENCE(150);

        /**
         * Unit vector along the region's axis.
         */
        private final float cos;
        /**
         * Unit vector along the region's axis.
         */
        private final float sin;

        /**
         * @param angle clockwise angle from the right of the core, in degrees
         */
        Sector(int angle) {
            cos = (float) Math.cos(Math.toRadians(angle));
            sin = (float) Math.sin(Math.toRadians(angle));
        }

        /**
         * @return suffix of the localized sector label
         */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Pairs of routes in stable catalog order.
     */
    public enum Route {
        /**
         * Expanded recipe storage.
         */
        PATTERN_LIBRARY(Sector.NETWORK),
        /**
         * Denser channel distribution.
         */
        CHANNEL_ROUTING(Sector.NETWORK),
        /**
         * Larger crafting batches.
         */
        MASS_PRODUCTION(Sector.CRAFTING),
        /**
         * Faster assembly.
         */
        OVERCLOCKING(Sector.CRAFTING),
        /**
         * Lower energy use.
         */
        EFFICIENCY(Sector.POWER),
        /**
         * Energy reserves and peak load.
         */
        POWER_RESERVE(Sector.POWER),
        /**
         * Deep storage cells.
         */
        DENSITY(Sector.STORAGE),
        /**
         * Storage filters and indexing.
         */
        PRECISION(Sector.STORAGE),
        /**
         * Faster dispatch and transfer.
         */
        THROUGHPUT(Sector.LOGISTICS),
        /**
         * Inventory buffers and stock control.
         */
        STOCKKEEPING(Sector.LOGISTICS),
        /**
         * Power and connection continuity.
         */
        REDUNDANCY(Sector.RESILIENCE),
        /**
         * Network visibility and alerts.
         */
        DIAGNOSTICS(Sector.RESILIENCE);

        /**
         * Region and purchase choice shared by this route and its opposite.
         */
        private final Sector sector;
        /**
         * Cached order for looking up the adjacent alternative.
         */
        private static final Route[] ROUTES = values();

        /**
         * @param sector shared region of the two routes
         */
        Route(Sector sector) {
            this.sector = sector;
        }

        /**
         * @return region to which this exclusive route belongs
         */
        public Sector sector() {
            return sector;
        }

        /**
         * @return opposite route at the same junction
         */
        public Route opposite() {
            return ROUTES[ordinal() ^ 1];
        }

        /**
         * @return suffix of the localized route label
         */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Visual tiers for starters, route choices and ordinary nodes.
     */
    public enum Kind {
        /**
         * Shared sector starter.
         */
        STARTER,
        /**
         * Commits this sector to one route.
         */
        CHOICE,
        /**
         * Small step inside a route.
         */
        PASSIVE,
        /**
         * The last node in a route.
         */
        CAPSTONE
    }

    /**
     * @return whether the node had a fixed effect in the original tree
     */
    public boolean legacyEffectNode() {
        return id < LEGACY_COUNT;
    }

    /**
     * @return all purchasable nodes in prerequisite order
     */
    public static List<UpgradeNode> all() {
        return NODES;
    }

    /**
     * Looks up a stable node ID from a save or action packet.
     *
     * @param id stable node index
     * @return matching node, or null if the ID is unknown
     */
    public static @Nullable UpgradeNode byId(int id) {
        return id >= 0 && id < NODES.size() ? NODES.get(id) : null;
    }

    /**
     * @return catalog preserving the original seven IDs and appending the prototype routes
     */
    private static List<UpgradeNode> build() {
        var nodes = new ArrayList<>(List.of(CHANNELS, PATTERNS, SPEED, BATCH, IDLE, CRAFT_ENERGY, OVERDRIVE));
        var storage = starter(nodes.size(), "storage", 0, Sector.STORAGE);
        nodes.add(storage);
        var logistics = starter(nodes.size(), "logistics", 0, Sector.LOGISTICS);
        nodes.add(logistics);
        var resilience = starter(nodes.size(), "resilience", 0, Sector.RESILIENCE);
        nodes.add(resilience);
        addRoute(nodes, Route.PATTERN_LIBRARY, CHANNELS, PATTERNS);
        addRoute(nodes, Route.CHANNEL_ROUTING, CHANNELS, null);
        addRoute(nodes, Route.MASS_PRODUCTION, SPEED, BATCH);
        addRoute(nodes, Route.OVERCLOCKING, SPEED, OVERDRIVE);
        addRoute(nodes, Route.EFFICIENCY, IDLE, CRAFT_ENERGY);
        addRoute(nodes, Route.POWER_RESERVE, IDLE, null);
        addRoute(nodes, Route.DENSITY, storage, null);
        addRoute(nodes, Route.PRECISION, storage, null);
        addRoute(nodes, Route.THROUGHPUT, logistics, null);
        addRoute(nodes, Route.STOCKKEEPING, logistics, null);
        addRoute(nodes, Route.REDUNDANCY, resilience, null);
        addRoute(nodes, Route.DIAGNOSTICS, resilience, null);
        return List.copyOf(nodes);
    }

    /**
     * Appends a route with two diamonds. Each diamond accepts either side as its prerequisite.
     *
     * @param nodes   catalog accumulated in prerequisite order
     * @param route   route to construct
     * @param starter shared prerequisite for both route choices
     * @param gateway existing functional gateway, or null to create a prototype
     */
    private static void addRoute(List<UpgradeNode> nodes, Route route, UpgradeNode starter, @Nullable UpgradeNode gateway) {
        if (gateway == null) {
            gateway = routeNode(nodes.size(), route.key() + ".1", 2, 0, route, 0, starter.id, ROOT);
            nodes.add(gateway);
        }
        int first = nodes.size();
        int[] prerequisites = {gateway.id, first, first, first + 1, first + 3, first + 3, first + 4};
        for (int i = 0; i < prerequisites.length; i++) {
            int alternate = switch (i) {
                case 3 -> first + 2;
                case 6 -> first + 5;
                default -> ROOT;
            };
            nodes.add(routeNode(nodes.size(), route.key() + "." + (i + 2), i == 6 ? 2 : 1, 0, route,
                    i + 1, prerequisites[i], alternate));
        }
    }

    /**
     * Builds a sector starter on its radial axis.
     *
     * @param id     stable node index
     * @param key    localization suffix
     * @param amount functional contribution, or zero for a prototype
     * @param sector map region
     * @return node connected directly to the ME core
     */
    private static UpgradeNode starter(int id, String key, int amount, Sector sector) {
        return new UpgradeNode(id, key, 1, amount, sector, null, ROOT, ROOT,
                HORIZONTAL_STRETCH * START_RADIUS * sector.cos, START_RADIUS * sector.sin, Kind.STARTER);
    }

    /**
     * Rotates a route node into its sector using {@code x = stretch*(r*cos - t*sin), y = r*sin + t*cos}.
     * Here {@code r} and {@code t} are the radial and signed side offsets for {@code step}, and the sine
     * and cosine are for the clockwise axis angle of {@code route}'s sector. {@code stretch} is
     * {@link #HORIZONTAL_STRETCH}.
     *
     * @param id          stable node index
     * @param key         localization suffix
     * @param cost        price in points
     * @param amount      functional contribution, or zero for a prototype
     * @param route       exclusive path containing the node
     * @param step        zero-based position on that path
     * @param previous    first acceptable prerequisite
     * @param alternative second acceptable prerequisite, or the absent sentinel
     * @return node with its computed map coordinates
     */
    private static UpgradeNode routeNode(int id, String key, int cost, int amount, Route route, int step,
                                         int previous, int alternative) {
        var sector = route.sector;
        int radial = RADIAL[step];
        int lateral = LATERAL[step] * (route.ordinal() % 2 == 0 ? 1 : -1);
        var kind = step == 0 ? Kind.CHOICE : step == RADIAL.length - 1 ? Kind.CAPSTONE : Kind.PASSIVE;
        return new UpgradeNode(id, key, cost, amount, sector, route, previous, alternative,
                HORIZONTAL_STRETCH * (radial * sector.cos - lateral * sector.sin),
                radial * sector.sin + lateral * sector.cos, kind);
    }
}
