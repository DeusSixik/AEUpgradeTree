package dev.sixik.aeupgrade.tree.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks configuration and the bounds of saved research-task progress. */
class ResearchTaskConfigTest {
    /**
     * Checks the preset written on a fresh install, its tier boundaries, and access to item rewards at the point cap.
     *
     * @param directory temporary server config directory
     * @throws IOException if the preset cannot be read from disk
     */
    @Test
    void load_freshConfigProvidesFourPlayableTaskPools(@TempDir Path directory) throws IOException {
        ResearchTaskConfig.load(directory, id -> false);
        try {
            var preset = ResearchTaskConfig.parse(Files.readString(directory.resolve(ResearchTaskConfig.FILE)));
            assertEquals(ResearchTaskConfig.state().document(), preset);
            assertFalse(preset.enabled());
            assertTrue(ResearchTaskConfig.current().tasks().isEmpty());
            var enabled = new ResearchTaskConfig.Document(true, preset.tasks(), preset.tiers());
            var active = ResearchTaskConfig.compile(enabled, id -> true);

            assertEquals(32, active.tasks().size());
            assertEquals(0, active.tier(9).id());
            assertEquals(1, active.tier(10).id());
            assertEquals(1, active.tier(29).id());
            assertEquals(2, active.tier(30).id());
            assertEquals(2, active.tier(69).id());
            var late = active.tier(70);
            assertEquals(3, late.id());
            assertEquals(late, active.tier(128));

            for (var tier : active.tiers()) {
                assertTrue(active.pools().get(tier.id()).size() > tier.visible());
            }
            var early = active.tier(0);
            var earlyPool = active.pools().get(early.id());
            assertTrue(earlyPool.stream().filter(task -> task.type() != ResearchTask.Type.DONATE_ITEMS).count()
                    < early.visible());

            var latePool = active.pools().get(late.id());
            long pointRewards = latePool.stream().filter(task -> task.points() > 0).count();
            assertTrue(pointRewards <= late.visible() - late.reroll());
            var repeatableItemRewards = latePool.stream()
                    .filter(task -> task.repeatable() && task.points() == 0 && !task.itemRewards().isEmpty()).toList();
            assertEquals(5, repeatableItemRewards.size());
            var random = new Random(3);
            var board = ResearchTaskBoard.EMPTY.refresh(late, latePool, id -> false, random);
            var itemOffers = board.offers().stream().filter(id -> active.byId().get(id).points() == 0).toList();
            assertTrue(itemOffers.size() >= late.reroll());
            for (String id : itemOffers.subList(0, late.reroll())) {
                board = board.complete(id, random);
            }
            assertTrue(board.refresh(late, latePool, id -> false, random).completed().isEmpty());
        } finally {
            ResearchTaskConfig.unload();
        }
    }

    /** @return a minimal item-independent job task that can run without the Minecraft registry */
    private static String firstCraft() {
        return """
                {"id":"pack:first","enabled":true,"type":"CRAFT_JOBS","amount":1,"points":2,"title":"First craft"}
                """;
    }

    /** Confirms the master switch and individual switches govern both display and progress tracking. */
    @Test
    void decode_masterAndTaskSwitches_hideDisabledTasks() {
        var dormant = firstCraft().replace("pack:first", "pack:dormant").replace("true", "false");
        String tasks = firstCraft() + "," + dormant;
        assertTrue(decode("{\"tasks\":[" + tasks + "]}").tasks().isEmpty());
        var config = decode("{\"enabled\":true,\"tasks\":[" + tasks + "]}");
        assertEquals(config.tasks(), config.crafting());
        assertEquals(1, config.tasks().size());
        assertEquals(2, config.byId().get("pack:first").points());
    }

    /** Rejects duplicate IDs, impossible counts and a donation with no configured item. */
    @Test
    void decode_malformedTaskDefinitions_rejectsConfiguration() {
        String start = "{\"enabled\":true,\"tasks\":[";
        assertThrows(IllegalArgumentException.class, () -> decode(start + firstCraft() + "," + firstCraft() + "]}"));
        assertThrows(IllegalArgumentException.class, () -> decode(start + firstCraft().replace("\"amount\":1", "\"amount\":0") + "]}"));
        assertThrows(IllegalArgumentException.class, () -> decode(start + firstCraft().replace("CRAFT_JOBS", "DONATE_ITEMS") + "]}"));
    }

    /**
     * Saves disabled definitions, applies current revisions, and rejects stale or invalid edits.
     *
     * @param directory temporary server config directory
     * @throws IOException if the test fixtures cannot be persisted
     */
    @Test
    void save_editPersistsAndStaleOrInvalidDraftLeavesConfirmedState(@TempDir Path directory) throws IOException {
        ResearchTaskConfig.load(directory, id -> false);
        try {
            long initialRevision = ResearchTaskConfig.state().revision();
            var dormant = firstCraft().replace("pack:first", "pack:dormant").replace("true", "false");
            var json = "{\"enabled\":true,\"tasks\":[" + firstCraft() + "," + dormant + "]}";
            var saved = ResearchTaskConfig.save(directory, json, initialRevision, id -> false);
            assertEquals(initialRevision + 1, saved.revision());
            assertEquals(2, saved.document().tasks().size());
            assertEquals(1, ResearchTaskConfig.current().tasks().size());
            var file = directory.resolve(ResearchTaskConfig.FILE);
            var stored = Files.readString(file);
            assertEquals(saved.document(), ResearchTaskConfig.parse(stored));
            assertNull(ResearchTaskConfig.save(directory, "{\"tasks\":[]}", initialRevision, id -> false));
            assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.save(directory,
                    json.replace("\"points\":2", "\"points\":0"), saved.revision(), id -> false));
            assertSame(saved, ResearchTaskConfig.state());
            assertEquals(stored, Files.readString(file));
            ResearchTaskConfig.load(directory, id -> false);
            assertEquals(saved.document(), ResearchTaskConfig.state().document());
        } finally {
            ResearchTaskConfig.unload();
        }
    }

    /**
     * Tests that an unavailable server item filter blocks activation of its task.
     *
     * @param directory temporary server config directory
     */
    @Test
    void save_registryRejectsInvalidEnabledItem(@TempDir Path directory) {
        ResearchTaskConfig.load(directory, id -> false);
        try {
            var original = ResearchTaskConfig.state();
            var json = """
                    {"enabled":true,"tasks":[{"id":"pack:donate","enabled":true,"type":"DONATE_ITEMS",
                    "item":"pack:missing","amount":1,"points":1,"title":"Donation"}]}
                    """;
            assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.save(directory, json, original.revision(), id -> false));
            assertSame(original, ResearchTaskConfig.state());
        } finally {
            ResearchTaskConfig.unload();
        }
    }

    /**
     * Parses and compiles a test config without a Minecraft item registry.
     *
     * @param json task config fixture
     * @return compiled tasks for assertions
     */
    private static ResearchTaskConfig.Active decode(String json) {
        return ResearchTaskConfig.compile(ResearchTaskConfig.parse(json), id -> false);
    }

    /**
     * Confirms the progress counter clips contributions to the remaining work.
     *
     * @param current saved progress in items or jobs
     * @param credit current contribution in items or jobs
     * @param goal configured target
     * @param expected new progress
     */
    @ParameterizedTest
    @CsvSource({"0,0,8,0", "2,2,8,4", "6,3,8,8", "4,9223372036854775807,8,8", "6,-4,8,6", "10,1,8,8"})
    void advance_partialOrOversizedContribution_capsAtGoal(int current, long credit, int goal, int expected) {
        assertEquals(expected, ResearchTask.advance(current, credit, goal));
    }

    /** Counts only completed requested items, and excludes storage donations from job credit. */
    @Test
    void craftCredit_countsMatchingOutputsOnly() {
        var order = new ResearchTask("pack:order", true, ResearchTask.Type.CRAFT_ITEMS, "ae2:logic_processor", 8, 2, "Order", null);
        assertEquals(0, order.craftCredit(null, 3));
        assertEquals(0, order.craftCredit("ae2:engineering_processor", 3));
        assertEquals(3, order.craftCredit("ae2:logic_processor", 3));
        assertEquals(8, order.craftCredit("ae2:logic_processor", Long.MAX_VALUE));
        var jobs = new ResearchTask("pack:jobs", true, ResearchTask.Type.CRAFT_JOBS, null, 2, 1, "Jobs", null);
        assertEquals(1, jobs.craftCredit(null, 80));
        var donate = new ResearchTask("pack:donate", true, ResearchTask.Type.DONATE_ITEMS, "ae2:logic_processor", 8, 2, "Donation", null);
        assertEquals(0, donate.craftCredit("ae2:logic_processor", 8));
    }
}
