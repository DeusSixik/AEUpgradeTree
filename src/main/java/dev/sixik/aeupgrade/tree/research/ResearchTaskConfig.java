package dev.sixik.aeupgrade.tree.research;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

/**
 * Publishes server-defined research tasks after validating and saving their configuration.
 */
public final class ResearchTaskConfig {
    /**
     * Relative path in NeoForge's config directory.
     */
    public static final String FILE = "aeupgrade/research-tasks.json";
    /**
     * Bundled UTF-8 preset for servers without a research task config.
     */
    private static final String BUNDLED_TASKS = "/data/aeupgrade/research_tasks.json";
    /**
     * Maximum configured tasks, including disabled entries.
     */
    public static final int MAX_TASKS = 64;
    /**
     * Maximum configured tiers, including the fallback.
     */
    public static final int MAX_TIERS = 32;
    /**
     * Maximum UTF-8 file size in bytes.
     */
    public static final int MAX_BYTES = 131_072;
    /**
     * Human-readable JSON serializer.
     */
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /**
     * Logger for startup validation and read/write failures.
     */
    private static final System.Logger LOGGER = System.getLogger(ResearchTaskConfig.class.getName());
    /**
     * Empty runtime state while the feature is disabled or there is no running server.
     */
    private static final Active DISABLED = new Active(List.of(), List.of(), Map.of(), List.of(ResearchTier.DEFAULT), Map.of());
    /**
     * Default config written on first startup with tasks disabled globally.
     */
    private static final Document EXAMPLES = loadPreset();
    /**
     * Sample draft and empty active tasks before the server reads its config.
     */
    private static final State INITIAL = new State(1, EXAMPLES, DISABLED);
    /**
     * Published task definition and compiled indexes, written on the server thread.
     */
    private static State state = INITIAL;

    /**
     * Keeps this lifecycle service static.
     */
    private ResearchTaskConfig() {
    }

    /**
     * Loads and validates the default disabled task preset without accessing Minecraft registries.
     *
     * @return the bundled tasks and tiers, or an empty disabled document if the resource is invalid
     */
    private static Document loadPreset() {
        try (var resource = ResearchTaskConfig.class.getResourceAsStream(BUNDLED_TASKS)) {
            if (resource == null) {
                throw new IOException("Missing " + BUNDLED_TASKS);
            }
            var preset = parse(new String(resource.readNBytes(MAX_BYTES + 1), StandardCharsets.UTF_8));
            var disabled = new Document(false, preset.tasks(), preset.tiers());
            compile(disabled, id -> false);
            return disabled;
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load the bundled research tasks.", failure);
            return new Document(false, List.of());
        }
    }

    /**
     * Loads a validated definition or writes the disabled sample file on a fresh installation.
     *
     * @param configDirectory server configuration directory
     * @param itemExists      lookup for currently registered items usable by tasks
     */
    public static void load(Path configDirectory, Predicate<String> itemExists) {
        state = INITIAL;
        var file = configDirectory.resolve(FILE);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, encode(EXAMPLES), StandardOpenOption.CREATE_NEW);
                return;
            }
            if (Files.size(file) > MAX_BYTES) {
                throw new IllegalArgumentException("Research task config exceeds " + MAX_BYTES + " bytes.");
            }
            var document = parse(Files.readString(file));
            encode(document);
            state = new State(INITIAL.revision(), document, compile(document, itemExists));
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load " + file + ". Research tasks are disabled for this server.", failure);
        }
    }

    /**
     * Clears the session config before another integrated server starts.
     */
    public static void unload() {
        state = INITIAL;
    }

    /**
     * @return compiled tasks for the current server
     */
    public static Active current() {
        return state.active();
    }

    /**
     * @return published document, indexes and revision for the operator editor
     */
    public static State state() {
        return state;
    }

    /**
     * Saves a validated draft and applies it immediately if the editor's revision is current.
     *
     * @param configDirectory server configuration directory
     * @param json            submitted draft
     * @param revision        revision the operator opened
     * @param itemExists      lookup for item IDs installed on the server
     * @return applied state, or null if the draft is stale
     * @throws IllegalArgumentException for invalid definitions
     * @throws IOException              if the config could not be saved
     */
    public static @Nullable State save(Path configDirectory, String json, long revision, Predicate<String> itemExists) throws IOException {
        if (state.revision() != revision) {
            return null;
        }
        var document = parse(json);
        var active = compile(document, itemExists);
        var file = configDirectory.resolve(FILE);
        Files.createDirectories(file.getParent());
        var temporary = Files.createTempFile(file.getParent(), "research-tasks-", ".tmp");
        try {
            Files.writeString(temporary, encode(document));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return state = new State(revision + 1, document, active);
    }

    /**
     * Reads a bounded task definition. Registry validation runs on the server before publishing it.
     *
     * @param json UTF-8 task config document
     * @return task settings including disabled entries
     * @throws IllegalArgumentException for invalid or oversized JSON
     */
    public static Document parse(String json) {
        checkSize(json);
        Document document;
        try {
            document = JSON.fromJson(json, Document.class);
        } catch (JsonParseException failure) {
            throw new IllegalArgumentException("Invalid research task JSON.", failure);
        }
        if (document == null || document.tasks == null || document.tasks.size() > MAX_TASKS) {
            throw new IllegalArgumentException("Expected at most " + MAX_TASKS + " research tasks.");
        }
        if (document.tasks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Missing research task.");
        }
        if (document.tiers().size() > MAX_TIERS) {
            throw new IllegalArgumentException("Expected at most " + MAX_TIERS + " research tiers.");
        }
        return new Document(document.enabled(), List.copyOf(document.tasks()), document.tiers());
    }

    /**
     * Serializes task settings for the editor and the server config.
     *
     * @param document task settings with disabled entries included
     * @return human-readable JSON
     * @throws IllegalArgumentException if the serial form exceeds the configured limit
     */
    public static String encode(Document document) {
        String json = JSON.toJson(document);
        checkSize(json);
        return json;
    }

    /**
     * Checks the UTF-8 size against the config and transport maximum.
     *
     * @param json incoming or outgoing task config
     * @throws IllegalArgumentException when the document exceeds the maximum size
     */
    private static void checkSize(String json) {
        if (json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Research task config exceeds " + MAX_BYTES + " bytes.");
        }
    }

    /**
     * Validates a draft and builds the indexes used by networks.
     *
     * @param document   parsed draft
     * @param itemExists registry lookup used for active item filters
     * @return immutable indexes of enabled tasks
     * @throws IllegalArgumentException for invalid task settings or duplicate IDs
     */
    static Active compile(Document document, Predicate<String> itemExists) {
        var byTier = new LinkedHashMap<Integer, ResearchTier>();
        for (var tier : document.tiers()) {
            tier.validate();
            if (byTier.putIfAbsent(tier.id(), tier) != null) {
                throw new IllegalArgumentException("Duplicate research tier " + tier.id() + ".");
            }
        }
        var ranges = byTier.values().stream().filter(tier -> tier.id() != 0).sorted(Comparator.comparingInt(ResearchTier::minPoints))
                .toList();
        for (int i = 1; i < ranges.size(); i++) {
            var previous = ranges.get(i - 1);
            var next = ranges.get(i);
            if (previous.maxPoints() == null || next.minPoints() < previous.maxPoints()) {
                throw new IllegalArgumentException("Overlapping research tiers " + previous.id() + " and " + next.id() + ".");
            }
        }
        var seen = new HashSet<String>();
        for (var task : document.tasks) {
            task.validate(document.enabled && Boolean.TRUE.equals(task.enabled()), itemExists);
            if (!byTier.containsKey(task.tier())) {
                throw new IllegalArgumentException("Unknown tier " + task.tier() + " on research task " + task.id() + ".");
            }
            if (!seen.add(task.id())) {
                throw new IllegalArgumentException("Duplicate research task " + task.id() + ".");
            }
        }
        if (!document.enabled) {
            return DISABLED;
        }
        var offered = document.tasks.stream().filter(task -> task.enabled()).toList();
        return new Active(offered, offered.stream().filter(task -> task.type() != ResearchTask.Type.DONATE_ITEMS).toList(),
                offered.stream().collect(Collectors.toUnmodifiableMap(ResearchTask::id, Function.identity())),
                List.copyOf(byTier.values()), Map.copyOf(offered.stream().collect(Collectors.groupingBy(ResearchTask::tier,
                Collectors.toUnmodifiableList()))));
    }

    /**
     * @param enabled master switch, false when missing
     * @param tasks   definitions in their display order
     * @param tiers   rules for the fallback and custom task pools
     */
    public record Document(boolean enabled, List<ResearchTask> tasks, List<ResearchTier> tiers) {
        /**
         * Adds the fallback tier to older configs or drafts where it is omitted.
         */
        public Document {
            if (tiers == null || tiers.isEmpty()) {
                tiers = List.of(ResearchTier.DEFAULT);
            } else if (tiers.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Missing research tier.");
            } else if (tiers.stream().noneMatch(tier -> tier.id() == 0)) {
                var defaults = new ArrayList<ResearchTier>();
                defaults.add(ResearchTier.DEFAULT);
                defaults.addAll(tiers);
                tiers = List.copyOf(defaults);
            } else {
                tiers = List.copyOf(tiers);
            }
        }

        /**
         * Creates a document with only fallback-tier settings.
         *
         * @param enabled master switch
         * @param tasks   configured objectives in display order
         */
        public Document(boolean enabled, List<ResearchTask> tasks) {
            this(enabled, tasks, null);
        }
    }

    /**
     * @param revision server-session revision for stale edit detection
     * @param document complete configuration including disabled tasks
     * @param active   indexes for progress tracking and viewing tasks
     */
    public record State(long revision, Document document, Active active) {
    }

    /**
     * @param tasks    enabled tasks in display order
     * @param crafting enabled tasks that track completed CPU jobs
     * @param byId     active tasks addressable by stable ID
     * @param tiers    available tier rules
     * @param pools    tasks grouped by their assigned tier
     */
    public record Active(List<ResearchTask> tasks, List<ResearchTask> crafting, Map<String, ResearchTask> byId,
                         List<ResearchTier> tiers, Map<Integer, List<ResearchTask>> pools) {
        /**
         * @param points current total research points
         * @return matching numbered tier, or tier zero when none apply
         */
        ResearchTier tier(int points) {
            ResearchTier fallback = ResearchTier.DEFAULT;
            for (var tier : tiers) {
                if (tier.id() == 0) {
                    fallback = tier;
                } else if (tier.matches(points)) {
                    return tier;
                }
            }
            return fallback;
        }
    }
}
