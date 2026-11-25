package dev.sixik.aeupgrade.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.aeupgrade.tree.UpgradeNode.Route;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;
import org.jetbrains.annotations.Nullable;

/**
 * Validated tree definition shared by the editor, saved purchases and runtime effects.
 */
public final class UpgradeCatalog {
    public static final int MAX_POINTS = 10_000;
    public static final int DEFAULT_REFUND_PERCENT = 50;
    /**
     * Maximum number of nodes in a server definition.
     */
    public static final int MAX_NODES = 512;
    /**
     * Upper bound for the next unused node ID.
     */
    public static final int ID_LIMIT = 65_536;
    /**
     * Maximum magnitude of a node coordinate, in world units.
     */
    public static final int MAX_COORDINATE = 4_000;
    public static final int MAX_TITLE_LENGTH = 80;
    /**
     * Maximum number of custom paths in a definition.
     */
    public static final int MAX_PATHS = MAX_NODES;
    /**
     * Original paths, whose identifiers and groups are reserved for compatibility.
     */
    private static final List<Path> BUILTIN_PATHS = Arrays.stream(Route.values())
            .map(route -> new Path(route.key(), "", route.sector(), "builtin:" + route.sector().key())).toList();
    /**
     * Legacy prototype tree used for compatibility with saved allocations.
     */
    public static final UpgradeCatalog DEFAULT = builtIn();

    /**
     * Serializable definition in editor display order.
     */
    private final Definition definition;
    private final UpgradeEffects coreEffects;
    /**
     * Entries indexed by their persistent node IDs.
     */
    private final Map<Integer, Entry> entries;
    /**
     * Nodes ordered so every prerequisite precedes its dependents.
     */
    private final List<UpgradeNode> ordered;
    /**
     * All paths, indexed by their stable identifiers.
     */
    private final Map<String, Path> paths;
    /**
     * Built-in and custom paths in editor order.
     */
    private final List<Path> pathOptions;
    /**
     * Symmetric conflicts compiled from the stored branch roots.
     */
    private final Map<Integer, BitSet> exclusions;
    /**
     * Continuations that cap choices among their incoming branches.
     */
    private final List<UpgradeNode> choiceGates;
    /**
     * Continuations indexed by their selectable incoming node IDs.
     */
    private final Map<Integer, List<UpgradeNode>> choiceGatesByInput;

    private static UpgradeCatalog builtIn() {
        var nodes = new ArrayList<>(UpgradeNode.all().stream().map(node -> new Entry(node, "", "", "")).toList());
        nodes.addAll(UpgradeTreeExamples.at(nodes.size(), 560, 0));
        return new UpgradeCatalog(new Definition(UpgradePlan.BUDGET, nodes.size(), UpgradePlan.OVERDRIVE_LIMIT, nodes));
    }

    /**
     * Validates a draft or config and builds indexes for runtime use.
     *
     * @param draft graph settings, positions and edges
     * @throws IllegalArgumentException if the definition has invalid settings or edges
     */
    public UpgradeCatalog(Definition draft) {
        if (draft == null || draft.budget() < 0 || draft.budget() > MAX_POINTS
                || draft.initialPoints() < 0 || draft.initialPoints() > draft.budget() || draft.nextId() < 7
                || draft.refundPercent() < 0 || draft.refundPercent() > 100
                || draft.nextId() > ID_LIMIT || draft.nodes() == null || draft.nodes().size() > MAX_NODES
                || draft.assemblerCap() < -1 || draft.assemblerCap() > 1_000_000) {
            throw new IllegalArgumentException("Invalid point settings, next ID, node count, or assembler cap.");
        }
        var byId = new HashMap<Integer, Entry>();
        var keys = new HashSet<String>();
        var normalized = new ArrayList<Entry>();
        var customPaths = draft.paths() == null ? List.<Path>of() : draft.paths();
        if (customPaths.size() > MAX_PATHS) {
            throw new IllegalArgumentException("Too many custom paths.");
        }
        var byPath = new HashMap<String, Path>();
        BUILTIN_PATHS.forEach(path -> byPath.put(path.id(), path));
        for (var path : customPaths) {
            validatePath(path);
            if (byPath.putIfAbsent(path.id(), path) != null) {
                throw new IllegalArgumentException("Duplicate path ID: " + path.id());
            }
        }
        for (var raw : draft.nodes()) {
            validate(raw, draft.nextId());
            if (raw.path() != null) {
                var path = byPath.get(raw.path());
                if (path == null || path.title().isEmpty() || raw.node().route() != null
                        || path.sector() != raw.node().sector()) {
                    throw new IllegalArgumentException("Invalid path on node " + raw.node().id() + ".");
                }
            }
            var effective = raw.effects() == null ? migrate(raw, draft.assemblerCap()) : raw;
            var entry = new Entry(raw.node(), raw.title(), raw.description(), raw.icon(), List.copyOf(effective.effects()),
                    raw.path(), raw.style() == null ? null : raw.style().validated(), raw.branches(), raw.reveal());
            normalized.add(entry);
            var node = entry.node();
            if (byId.putIfAbsent(node.id(), entry) != null || !keys.add(node.key())) {
                throw new IllegalArgumentException("Duplicate node ID or key: " + node.id());
            }
        }
        ordered = List.copyOf(orderNodes(normalized, byId));
        paths = Map.copyOf(byPath);
        var options = new ArrayList<>(BUILTIN_PATHS);
        options.addAll(customPaths);
        pathOptions = List.copyOf(options);
        var explicit = normalized.stream().map(entry -> {
            var branches = entry.branches() == null ? legacyBranches(entry, normalized, byId) : entry.branches();
            var seen = new HashSet<Integer>();
            for (var id : branches) {
                if (id == null || id == entry.node().id() || !byId.containsKey(id) || !seen.add(id)) {
                    throw new IllegalArgumentException("Invalid branch root on node " + entry.node().id() + ": " + id);
                }
            }
            return entry.withBranches(List.copyOf(branches));
        }).toList();
        explicit.forEach(entry -> byId.put(entry.node().id(), entry));
        var core = draft.core().validated();
        definition = new Definition(draft.budget(), draft.nextId(), null, explicit, List.copyOf(customPaths),
                draft.experimental(), draft.nodeSettings(), core, draft.startingPoints(), draft.operatorOnly(),
                draft.refundPercent(), draft.dropResearchOnBreak());
        coreEffects = UpgradeEffects.EMPTY.add(UpgradeNode.ROOT, core.effects());
        entries = Map.copyOf(byId);
        exclusions = compileExclusions(explicit, ordered);
        choiceGates = ordered.stream()
                .filter(node -> node.locksRemaining() && node.requiredCount() < node.prerequisiteCount())
                .toList();
        var byInput = new HashMap<Integer, List<UpgradeNode>>();
        for (var gate : choiceGates) {
            for (int input : gate.prerequisiteIds()) {
                byInput.computeIfAbsent(input, ignored -> new ArrayList<>()).add(gate);
            }
        }
        byInput.replaceAll((id, gates) -> List.copyOf(gates));
        choiceGatesByInput = Map.copyOf(byInput);
    }

    /**
     * @return serializable tree settings
     */
    public Definition definition() {
        return definition;
    }

    /**
     * @return effects active on the controller before any nodes are purchased
     */
    UpgradeEffects coreEffects() {
        return coreEffects;
    }

    /**
     * @return maximum total points, including allocated points
     */
    public int budget() {
        return definition.budget();
    }

    /**
     * @return nodes in dependency order, with every prerequisite before its dependents
     */
    public List<UpgradeNode> ordered() {
        return ordered;
    }

    /**
     * Looks up a live node.
     *
     * @param id persistent node ID
     * @return configured node, or null if removed
     */
    public @Nullable UpgradeNode node(int id) {
        var entry = entries.get(id);
        return entry == null ? null : entry.node();
    }

    /**
     * Finds the configured presentation of a node.
     *
     * @param id persistent node ID
     * @return configured entry, or null if removed
     */
    public @Nullable Entry entry(int id) {
        return entries.get(id);
    }

    /**
     * @return built-in and custom paths in display order
     */
    public List<Path> paths() {
        return pathOptions;
    }

    /**
     * @param nodeId stable ID of the node
     * @return configured path, or null for an unassigned or absent node
     */
    public @Nullable Path pathOf(int nodeId) {
        var entry = entries.get(nodeId);
        return pathOf(entry);
    }

    private @Nullable Path pathOf(@Nullable Entry entry) {
        var id = entry == null ? null : entry.pathId();
        return id == null ? null : paths.get(id);
    }

    /**
     * Finds the entry points of the old mutually exclusive paths when reading an old document.
     */
    private List<Integer> legacyBranches(Entry entry, List<Entry> nodes, Map<Integer, Entry> byId) {
        var ownPath = pathOf(entry);
        if (ownPath == null || ownPath.exclusiveGroup() == null) {
            return List.of();
        }
        var result = new ArrayList<Integer>();
        for (var candidate : nodes) {
            var path = pathOf(candidate);
            if (path == null || path.equals(ownPath) || !ownPath.exclusiveGroup().equals(path.exclusiveGroup())) {
                continue;
            }
            var node = candidate.node();
            if (node.prerequisiteCount() == 0
                    || node.prerequisiteIds().stream().anyMatch(id -> !path.equals(pathOf(byId.get(id))))) {
                result.add(node.id());
            }
        }
        return result;
    }

    /**
     * Includes descendants that lose too many inputs when a branch closes.
     */
    private static Map<Integer, BitSet> compileExclusions(List<Entry> entries, List<UpgradeNode> order) {
        var conflicts = new HashMap<Integer, BitSet>();
        for (var entry : entries) {
            if (entry.branches().isEmpty()) {
                continue;
            }
            var closed = new BitSet();
            entry.branches().forEach(closed::set);
            for (var node : order) {
                int remaining = node.prerequisiteCount();
                for (int i = 0; i < node.prerequisiteCount() && remaining >= node.requiredCount(); i++) {
                    if (closed.get(node.prerequisiteId(i))) {
                        remaining--;
                    }
                }
                if (remaining < node.requiredCount()) {
                    closed.set(node.id());
                }
            }
            int source = entry.node().id();
            closed.clear(source);
            closed.stream().forEach(target -> {
                conflicts.computeIfAbsent(source, ignored -> new BitSet()).set(target);
                conflicts.computeIfAbsent(target, ignored -> new BitSet()).set(source);
            });
        }
        return Map.copyOf(conflicts);
    }

    /**
     * @param nodeId newly purchased node ID
     * @return whether the purchase can close other branches
     */
    boolean hasExclusions(int nodeId) {
        return exclusions.containsKey(nodeId) || choiceGatesByInput.containsKey(nodeId);
    }

    /**
     * @param nodeId selectable upgrade
     * @return continuations whose branch limit this purchase counts toward
     */
    public List<UpgradeNode> choiceGatesFor(int nodeId) {
        return choiceGatesByInput.getOrDefault(nodeId, List.of());
    }

    /**
     * @param purchased allocated node IDs
     * @return reason for each closed node, including inputs lost across several choices
     */
    Map<Integer, Blocker> blockingNodes(BitSet purchased) {
        var blocked = new HashMap<Integer, Blocker>();
        for (int id = purchased.nextSetBit(0); id >= 0; id = purchased.nextSetBit(id + 1)) {
            var conflicts = exclusions.get(id);
            if (conflicts != null) {
                var buyer = new Blocker(node(id), false);
                conflicts.stream().forEach(target -> blocked.putIfAbsent(target, buyer));
            }
        }
        for (var gate : choiceGates) {
            if (gate.prerequisiteIds().stream().filter(purchased::get).count() >= gate.requiredCount()) {
                var limit = new Blocker(gate, true);
                for (int input : gate.prerequisiteIds()) {
                    if (!purchased.get(input)) {
                        blocked.putIfAbsent(input, limit);
                    }
                }
            }
        }
        for (var node : ordered) {
            if (blocked.containsKey(node.id())) {
                continue;
            }
            int remaining = node.prerequisiteCount();
            for (int i = 0; i < node.prerequisiteCount(); i++) {
                var blocker = blocked.get(node.prerequisiteId(i));
                if (blocker != null && --remaining < node.requiredCount()) {
                    blocked.put(node.id(), blocker);
                    break;
                }
            }
        }
        return Map.copyOf(blocked);
    }

    /**
     * @param source      purchased conflict or continuation with a full branch quota
     * @param choiceLimit whether a branch quota caused the lock
     */
    record Blocker(UpgradeNode source, boolean choiceLimit) {
    }

    /**
     * @param path custom path settings
     * @throws IllegalArgumentException if the ID, title, sector, or optional group is invalid
     */
    public static void validatePath(@Nullable Path path) {
        if (path == null || path.id() == null || !path.id().matches("[a-z0-9_.-]{1,80}")
                || !validText(path.title(), 80) || path.title().isBlank() || path.sector() == null
                || path.exclusiveGroup() != null && !path.exclusiveGroup().matches("[a-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("Invalid custom path settings.");
        }
    }

    /**
     * Wires an incoming prerequisite on a draft node without changing the draft.
     *
     * @param draft  graph with the current nodes
     * @param source prerequisite ID or the ME core
     * @param target dependent node ID
     * @return target with the incoming edge added
     * @throws IllegalArgumentException for a missing endpoint, duplicate, or invalid graph
     */
    public static Entry connect(List<Entry> draft, int source, int target) {
        var byId = new HashMap<Integer, Entry>();
        for (var entry : draft) {
            byId.put(entry.node().id(), entry);
        }
        var entry = target == UpgradeNode.ROOT ? null : byId.get(target);
        if (entry == null || source != UpgradeNode.ROOT && !byId.containsKey(source)) {
            throw new IllegalArgumentException("Missing connection endpoint.");
        }
        var node = entry.node();
        var parents = new ArrayList<>(node.prerequisiteIds());
        if (parents.contains(source) || source == UpgradeNode.ROOT && node.connectsToCore()) {
            throw new IllegalArgumentException("The connection already exists.");
        }
        if (source == UpgradeNode.ROOT && !parents.isEmpty()) {
            throw new IllegalArgumentException("A direct core link cannot be combined with node prerequisites.");
        }
        parents.add(source);
        var connected = entry.withNode(node.withPrerequisites(parents,
                source == UpgradeNode.ROOT ? 0 : Math.max(1, node.requiredCount())));
        byId.put(target, connected);
        orderNodes(byId.values(), byId);
        return connected;
    }

    /**
     * Checks node settings independently of its inbound links.
     *
     * @param entry  proposed node and its display overrides
     * @param nextId first unissued ID
     * @throws IllegalArgumentException if any node setting is invalid
     */
    private static void validate(Entry entry, int nextId) {
        if (entry == null || entry.node() == null) {
            throw new IllegalArgumentException("Missing node.");
        }
        var node = entry.node();
        var builtin = UpgradeNode.byId(node.id());
        if (node.id() < 0 || node.id() >= nextId || node.key() == null || !node.key().matches("[a-z0-9_.-]{1,80}")
                || node.id() < 7 && !builtin.key().equals(node.key())
                || node.cost() < 0 || node.cost() > 10_000 || node.sector() == null || node.kind() == null
                || !validCoordinate(node.x()) || !validCoordinate(node.y())
                || node.previous() < UpgradeNode.DISCONNECTED || node.alternative() < UpgradeNode.ROOT
                || node.previous() < 0 && node.alternative() != UpgradeNode.ROOT
                || node.alternative() != UpgradeNode.ROOT && node.previous() == node.alternative()
                || node.route() != null && node.route().sector() != node.sector()
                || !validText(entry.title(), MAX_TITLE_LENGTH) || !validText(entry.description(), 240)
                || !validText(entry.icon(), 100) || !entry.icon().isEmpty() && !entry.icon().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new IllegalArgumentException("Invalid settings for node " + node.id() + ".");
        }
        if (entry.effects() != null && entry.effects().stream().anyMatch(effect -> effect == null || !effect.valid())) {
            throw new IllegalArgumentException("Invalid effect on node " + node.id() + ".");
        }
        var routedSources = new HashSet<Integer>();
        for (var link : node.routes()) {
            if (!node.connectedFrom(link.source()) || !routedSources.add(link.source())) {
                throw new IllegalArgumentException("Invalid link route on node " + node.id() + ".");
            }
        }
    }

    /**
     * Identifies a finite world coordinate within the tree's saved map bounds.
     */
    public static boolean validCoordinate(float value) {
        return Math.abs(value) <= MAX_COORDINATE;
    }

    /**
     * Maps the old fixed effects to data-driven entries. An explicitly empty effect list stays empty.
     *
     * @param entry node from the original config format
     * @param cap   old Overdrive assembler cap, or -1 if unlimited
     * @return entry with a complete effect list
     */
    private static Entry migrate(Entry entry, int cap) {
        var node = entry.node();
        var kind = switch (node.id()) {
            case 0 -> Kind.CHANNELS;
            case 1 -> Kind.PATTERN_SLOTS;
            case 2, 6 -> Kind.SPEED_BASE;
            case 3 -> Kind.BATCH;
            case 4 -> Kind.IDLE_PERCENT;
            case 5 -> Kind.CRAFT_PERCENT;
            default -> null;
        };
        var effects = new ArrayList<UpgradeEffect>(2);
        if (kind != null) {
            effects.add(new UpgradeEffect(kind, Target.ALL, node.amount()));
        }
        if (node.id() == UpgradeNode.OVERDRIVE.id() && cap >= 0) {
            effects.add(new UpgradeEffect(Kind.DEVICE_CAP, Target.MOLECULAR_ASSEMBLER, cap));
        }
        return new Entry(node, entry.title(), entry.description(), entry.icon(), List.copyOf(effects), entry.path());
    }

    /**
     * @param value   configured text
     * @param maximum maximum length in Java characters
     * @return whether the text is present and fits the field
     */
    private static boolean validText(@Nullable String value, int maximum) {
        return value != null && value.length() <= maximum;
    }

    /**
     * @param entries draft traversal order
     * @param byId    draft nodes by stable ID
     * @return nodes in prerequisite order
     * @throws IllegalArgumentException for missing links or cycles
     */
    private static List<UpgradeNode> orderNodes(Iterable<Entry> entries, Map<Integer, Entry> byId) {
        var sorted = new ArrayList<UpgradeNode>();
        var visiting = new HashSet<Integer>();
        var visited = new HashSet<Integer>();
        for (var entry : entries) {
            visit(entry.node(), byId, visiting, visited, sorted);
        }
        return sorted;
    }

    /**
     * Orders the node after its parents and rejects missing links or cycles.
     *
     * @param node     current node
     * @param entries  all entries by ID
     * @param visiting IDs on the current traversal stack
     * @param visited  IDs already sorted
     * @param sorted   dependency order built so far
     * @throws IllegalArgumentException on a missing parent or a cycle
     */
    private static void visit(UpgradeNode node, Map<Integer, Entry> entries, Set<Integer> visiting,
                              Set<Integer> visited, List<UpgradeNode> sorted) {
        if (visited.contains(node.id())) {
            return;
        }
        if (!visiting.add(node.id())) {
            throw new IllegalArgumentException("Cycle at node " + node.id() + ".");
        }
        for (int i = 0; i < node.prerequisiteCount(); i++) {
            int id = node.prerequisiteId(i);
            var parent = entries.get(id);
            if (parent == null) {
                throw new IllegalArgumentException("Node " + node.id() + " refers to missing node " + id + ".");
            }
            visit(parent.node(), entries, visiting, visited, sorted);
        }
        visiting.remove(node.id());
        visited.add(node.id());
        sorted.add(node);
    }

    /**
     * @param budget              points available to each network
     * @param nextId              first never-issued ID
     * @param overdriveLimit      assembler cap when Overdrive is purchased, or null for the original cap
     * @param nodes               nodes in display order
     * @param paths               custom paths in display order, or null in older configs
     * @param experimental        optional rendering effects in the tree view
     * @param nodeSettings        shared node tooltip settings
     * @param core                presentation and always-active effects of the starting node
     * @param startingPoints      initial network points or null to use the legacy budget
     * @param operatorOnly        whether research is restricted to operators, false when omitted
     * @param refundPercent       percent of points recovered by respecs or controller destruction
     * @param dropResearchOnBreak whether the final controller block drops certificates, true when omitted
     */
    public record Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                             @Nullable List<Path> paths, ExperimentalSettings experimental, NodeSettings nodeSettings,
                             Core core,
                             @Nullable Integer startingPoints, Boolean operatorOnly, Integer refundPercent,
                             Boolean dropResearchOnBreak) {
        public Definition {
            if (experimental == null) {
                experimental = ExperimentalSettings.DEFAULT;
            }
            if (nodeSettings == null) {
                nodeSettings = NodeSettings.DEFAULT;
            }
            if (core == null) {
                core = Core.DEFAULT;
            }
            operatorOnly = Boolean.TRUE.equals(operatorOnly);
            refundPercent = refundPercent == null ? DEFAULT_REFUND_PERCENT : refundPercent;
            dropResearchOnBreak = !Boolean.FALSE.equals(dropResearchOnBreak);
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths, ExperimentalSettings experimental, NodeSettings nodeSettings, Core core,
                          @Nullable Integer startingPoints, Boolean operatorOnly, Integer refundPercent) {
            this(budget, nextId, overdriveLimit, nodes, paths, experimental, nodeSettings, core, startingPoints,
                    operatorOnly, refundPercent, true);
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths, ExperimentalSettings experimental, NodeSettings nodeSettings, Core core,
                          @Nullable Integer startingPoints, Boolean operatorOnly) {
            this(budget, nextId, overdriveLimit, nodes, paths, experimental, nodeSettings, core, startingPoints,
                    operatorOnly, DEFAULT_REFUND_PERCENT);
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths, ExperimentalSettings experimental, NodeSettings nodeSettings, Core core) {
            this(budget, nextId, overdriveLimit, nodes, paths, experimental, nodeSettings, core, null, false);
        }

        /**
         * @return starting points for a network without saved research
         */
        public int initialPoints() {
            return startingPoints == null ? budget : startingPoints;
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths, ExperimentalSettings experimental, NodeSettings nodeSettings) {
            this(budget, nextId, overdriveLimit, nodes, paths, experimental, nodeSettings, Core.DEFAULT);
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths, ExperimentalSettings experimental) {
            this(budget, nextId, overdriveLimit, nodes, paths, experimental, NodeSettings.DEFAULT);
        }

        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes,
                          @Nullable List<Path> paths) {
            this(budget, nextId, overdriveLimit, nodes, paths, ExperimentalSettings.DEFAULT);
        }

        /**
         * @param budget         points available to each network
         * @param nextId         first never-issued ID
         * @param overdriveLimit legacy assembler cap
         * @param nodes          nodes in display order
         */
        public Definition(int budget, int nextId, @Nullable Integer overdriveLimit, List<Entry> nodes) {
            this(budget, nextId, overdriveLimit, nodes, List.of());
        }

        /**
         * @return configured cap, using -1 for unlimited
         */
        public int assemblerCap() {
            return overdriveLimit == null ? UpgradePlan.OVERDRIVE_LIMIT : overdriveLimit;
        }
    }

    /**
     * @param title       custom title or empty for the localized ME Core title
     * @param description custom tooltip text
     * @param icon        registry ID or empty for the ME Controller item
     * @param effects     starting effects for a controller grid
     * @param x           horizontal map coordinate in world units
     * @param y           vertical map coordinate in world units
     * @param style       appearance overrides or null for the original core appearance
     */
    public record Core(String title, String description, String icon, List<UpgradeEffect> effects, float x, float y,
                       @Nullable UpgradeNodeStyle style) {
        public static final Core DEFAULT = new Core("", "", "", List.of(), 0, 0, null);

        public Core {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            icon = icon == null ? "" : icon;
            effects = effects == null ? List.of() : effects;
        }

        private Core validated() {
            if (!validText(title, MAX_TITLE_LENGTH) || !validText(description, 240) || !validText(icon, 100)
                    || !icon.isEmpty() && !icon.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")
                    || !validCoordinate(x) || !validCoordinate(y)
                    || effects.stream().anyMatch(effect -> effect == null || !effect.valid())) {
                throw new IllegalArgumentException("Invalid ME core settings.");
            }
            return new Core(title, description, icon, List.copyOf(effects), x, y, style == null ? null : style.validated());
        }

        /**
         * @return presentation compatible with the ordinary node widgets and inspector
         */
        public Entry asEntry() {
            var node = new UpgradeNode(UpgradeNode.ROOT, "core", 0, 0, UpgradeNode.Sector.NETWORK, null,
                    UpgradeNode.DISCONNECTED, UpgradeNode.ROOT, x, y, UpgradeNode.Kind.STARTER);
            return new Entry(node, title, description, icon, effects, null, style, List.of());
        }

        /**
         * @param entry the editor's representation of the core
         * @return only the fields supported on a starting node
         */
        public static Core fromEntry(Entry entry) {
            return new Core(entry.title(), entry.description(), entry.icon(), entry.effects(), entry.node().x(),
                    entry.node().y(), entry.style());
        }
    }

    /**
     * @param showRequirements whether tooltips show the prerequisite rule and its progress
     */
    public record NodeSettings(Boolean showRequirements) {
        public static final NodeSettings DEFAULT = new NodeSettings(true);

        public NodeSettings {
            showRequirements = !Boolean.FALSE.equals(showRequirements);
        }
    }

    /**
     * @param starfield           whether to draw the animated starfield behind unlocked nodes
     * @param animatedLinks       whether to animate current and sparks along links
     * @param availableNodePulses whether to pulse available nodes and their powered incoming links
     */
    public record ExperimentalSettings(Boolean starfield, Boolean animatedLinks, Boolean availableNodePulses) {
        public static final ExperimentalSettings DEFAULT = new ExperimentalSettings(true, true, true);

        public ExperimentalSettings {
            // Older definitions can omit any of these settings.
            starfield = !Boolean.FALSE.equals(starfield);
            animatedLinks = !Boolean.FALSE.equals(animatedLinks);
            availableNodePulses = !Boolean.FALSE.equals(availableNodePulses);
        }

        /**
         * @param starfield     whether to draw the starfield
         * @param animatedLinks whether to animate current and sparks along links
         */
        public ExperimentalSettings(Boolean starfield, Boolean animatedLinks) {
            this(starfield, animatedLinks, true);
        }
    }

    /**
     * @param id             stable identifier
     * @param title          display name, or empty for a localized built-in path
     * @param sector         theme and icon family for member nodes
     * @param exclusiveGroup shared group key, or null for an independent path
     */
    public record Path(String id, String title, Sector sector, @Nullable String exclusiveGroup) {
    }

    /**
     * @param hideDescendantsUntilUnlocked whether outgoing paths are hidden until the node is purchased
     * @param hideUntilPrerequisites       whether the node is hidden until its purchase prerequisites are met
     */
    public record Reveal(boolean hideDescendantsUntilUnlocked, boolean hideUntilPrerequisites) {
        private static final Reveal DEFAULT = new Reveal(false, false);
    }

    /**
     * @param node        graph and gameplay settings
     * @param title       custom title, or empty for the localized default
     * @param description extra tooltip text
     * @param icon        item registry ID, or empty for the default AE2 icon
     * @param effects     active modifiers, or null while migrating the legacy format
     * @param path        custom path ID, or null for a built-in path or no path
     * @param style       appearance overrides
     * @param branches    roots of conflicting branches
     * @param reveal      visibility overrides for the player map
     */
    public record Entry(UpgradeNode node, String title, String description, String icon,
                        @Nullable List<UpgradeEffect> effects,
                        @Nullable String path, @Nullable UpgradeNodeStyle style, @Nullable List<Integer> branches,
                        @Nullable Reveal reveal) {
        public Entry {
            if (Reveal.DEFAULT.equals(reveal)) {
                reveal = null;
            }
        }

        public Entry(UpgradeNode node, String title, String description, String icon, @Nullable List<UpgradeEffect> effects,
                     @Nullable String path, @Nullable UpgradeNodeStyle style, @Nullable List<Integer> branches) {
            this(node, title, description, icon, effects, path, style, branches, null);
        }

        /**
         * @param node        legacy graph node
         * @param title       custom title
         * @param description extra tooltip text
         * @param icon        item registry ID
         */
        public Entry(UpgradeNode node, String title, String description, String icon) {
            this(node, title, description, icon, null, null, null, null);
        }

        /**
         * @param node        graph and gameplay settings
         * @param title       custom title
         * @param description extra tooltip text
         * @param icon        item registry ID
         * @param effects     active modifiers
         */
        public Entry(UpgradeNode node, String title, String description, String icon, @Nullable List<UpgradeEffect> effects) {
            this(node, title, description, icon, effects, null, null, null);
        }

        public Entry(UpgradeNode node, String title, String description, String icon, @Nullable List<UpgradeEffect> effects,
                     @Nullable String path) {
            this(node, title, description, icon, effects, path, null, null);
        }

        /**
         * @return built-in or custom path identifier, or null when unassigned
         */
        public @Nullable String pathId() {
            return path != null ? path : node.route() == null ? null : node.route().key();
        }

        /**
         * @param replacement updated node data
         * @return entry preserving the path, effects, and presentation
         */
        public Entry withNode(UpgradeNode replacement) {
            return new Entry(replacement, title, description, icon, effects, path, style, branches, reveal);
        }

        public Entry withBranches(List<Integer> replacement) {
            return new Entry(node, title, description, icon, effects, path, style, replacement, reveal);
        }

        /**
         * @return visibility rules, with both gates disabled for legacy entries
         */
        public Reveal revealRules() {
            return reveal == null ? Reveal.DEFAULT : reveal;
        }

        public UpgradeNodeStyle appearance() {
            return style == null ? UpgradeNodeStyle.defaults(node) : style;
        }
    }
}
