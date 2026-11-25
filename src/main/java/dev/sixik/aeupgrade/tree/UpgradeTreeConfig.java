package dev.sixik.aeupgrade.tree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.sixik.aeupgrade.platform.Platform;
import org.slf4j.Logger;

/**
 * Loads the shared server tree and publishes changes only after writing its config.
 */
public final class UpgradeTreeConfig {
    /**
     * Maximum serialized definition length in UTF-8 bytes.
     */
    public static final int MAX_BYTES = 524_288;
    /**
     * Name within the loader's config directory.
     */
    public static final String FILE = "aeupgrade/upgrade-tree.json";
    /**
     * Tree shipped for worlds that do not have a server definition yet.
     */
    private static final String BUNDLED_TREE = "/data/aeupgrade/upgrade_tree.json";

    /**
     * Serializer used for client documents and human-editable settings.
     */
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /**
     * Reports IO and parsing errors to the server log.
     */
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Validated first-run definition shared across server lifecycles.
     */
    private static final State DEFAULT = initialState();
    /**
     * Current server document. Mutations run on the server thread.
     */
    private static State state = DEFAULT;

    /**
     * Keeps the shared config service static.
     */
    private UpgradeTreeConfig() {
    }

    /**
     * @return current server graph and revision
     */
    public static State current() {
        return state;
    }

    /**
     * Loads the definition before grids are created and writes the default on first run.
     */
    public static void load() {
        state = DEFAULT;
        Path file = Platform.configDirectory().resolve(FILE);
        try {
            if (Files.notExists(file)) {
                write(file, state.json());
            } else {
                if (Files.size(file) > MAX_BYTES) {
                    throw new IllegalArgumentException("Tree config exceeds " + MAX_BYTES + " bytes.");
                }
                var catalog = decode(Files.readString(file, StandardCharsets.UTF_8));
                state = new State(state.revision(), catalog, encode(catalog.definition()));
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not load {}. Using the built-in tree for this server.", file, failure);
        }
    }

    /**
     * Clears the active server definition on shutdown.
     */
    public static void unload() {
        state = DEFAULT;
    }

    /**
     * Validates and saves a replacement document at the expected revision.
     *
     * @param json     proposed tree document
     * @param revision revision originally opened by the editor
     * @return updated state, or null when the draft is stale
     * @throws IllegalArgumentException on invalid fields, links or reused IDs
     * @throws IOException              when the config cannot be written
     */
    public static @org.jetbrains.annotations.Nullable State save(String json, long revision) throws IOException {
        if (state.revision() != revision) {
            return null;
        }
        var catalog = decode(json);
        int nextId = state.catalog().definition().nextId();
        if (catalog.definition().nextId() < nextId || catalog.definition().nodes().stream()
                .anyMatch(entry -> entry.node().id() < nextId && state.catalog().node(entry.node().id()) == null)) {
            throw new IllegalArgumentException("Removed node IDs cannot be reused.");
        }
        String encoded = encode(catalog.definition());
        write(Platform.configDirectory().resolve(FILE), encoded);
        state = new State(revision + 1, catalog, encoded);
        return state;
    }

    /**
     * Parses and validates a graph definition.
     *
     * @param json UTF-8 JSON document
     * @return valid definition with indexes and dependency order
     * @throws IllegalArgumentException for invalid or oversized documents
     */
    public static UpgradeCatalog decode(String json) {
        checkSize(json);
        try {
            return new UpgradeCatalog(JSON.fromJson(json, UpgradeCatalog.Definition.class));
        } catch (com.google.gson.JsonParseException failure) {
            throw new IllegalArgumentException("Invalid tree JSON.", failure);
        }
    }

    /**
     * Serializes a draft or active definition.
     *
     * @param definition tree settings
     * @return readable JSON within the config size limit
     * @throws IllegalArgumentException if the result is too large
     */
    public static String encode(UpgradeCatalog.Definition definition) {
        String json = JSON.toJson(definition);
        checkSize(json);
        return json;
    }

    /**
     * @param json document to check
     * @throws IllegalArgumentException if the UTF-8 document exceeds the limit
     */
    private static void checkSize(String json) {
        if (json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Tree config exceeds " + MAX_BYTES + " bytes.");
        }
    }

    /**
     * @return bundled definition and its encoded snapshot, or the legacy catalog as a fallback
     */
    private static State initialState() {
        try (var resource = UpgradeTreeConfig.class.getResourceAsStream(BUNDLED_TREE)) {
            if (resource == null) {
                throw new IOException("Missing " + BUNDLED_TREE);
            }
            var catalog = decode(new String(resource.readNBytes(MAX_BYTES + 1), StandardCharsets.UTF_8));
            return new State(1, catalog, encode(catalog.definition()));
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not load the bundled upgrade tree.", failure);
            return new State(1, UpgradeCatalog.DEFAULT, encode(UpgradeCatalog.DEFAULT.definition()));
        }
    }

    /**
     * Writes the full document to a temporary sibling before replacing the old config.
     *
     * @param file destination under config
     * @param json validated definition
     * @throws IOException if the filesystem cannot commit the file
     */
    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        var temporary = Files.createTempFile(file.getParent(), "upgrade-tree-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * @param revision server-local revision used for optimistic concurrency
     * @param catalog  validated active graph
     * @param json     complete graph document sent to clients
     */
    public record State(long revision, UpgradeCatalog catalog, String json) {
    }
}
