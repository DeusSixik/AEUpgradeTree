package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.Gson;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.ExperimentalSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.NodeSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Path;
import dev.sixik.aeupgrade.tree.UpgradeNode.Kind;
import dev.sixik.aeupgrade.tree.UpgradeNode.Route;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks editor definitions and the migration of purchases when a tree changes. */
class UpgradeCatalogTest {
    @Test
    void experimentalSettings_missingOptionsKeepTheirDefaultAndExplicitOptionsSurviveValidation() {
        var entries = List.of(entry(102, UpgradeNode.ROOT, 1));
        var legacy = new UpgradeCatalog(new Definition(10, 103, null, entries, null, null));
        assertEquals(ExperimentalSettings.DEFAULT, legacy.definition().experimental());

        var options = new ExperimentalSettings(false, null);
        var configured = new UpgradeCatalog(new Definition(10, 103, null, entries, null, options));
        assertEquals(new ExperimentalSettings(false, true), configured.definition().experimental());
    }

    @Test
    void availableNodePulses_legacyConfigEnablesPulsesAndExplicitSettingSurvivesRoundTrip() {
        var gson = new Gson();
        var legacy = gson.fromJson("{\"experimental\":{\"starfield\":false,\"animatedLinks\":false}}", Definition.class);
        assertEquals(new ExperimentalSettings(false, false, true), legacy.experimental());

        var settings = new ExperimentalSettings(false, true, false);
        var configured = new Definition(10, 103, null, List.of(entry(102, UpgradeNode.ROOT, 1)), null, settings);
        var reloaded = new UpgradeCatalog(gson.fromJson(gson.toJson(configured), Definition.class));
        assertEquals(settings, reloaded.definition().experimental());
    }

    @Test
    void newlyClosedBranches_symmetricConflict_warnsFromEitherSideAndSkipsAlreadyClosedBranches() {
        var catalog = new UpgradeCatalog(new Definition(10, 107, null, List.of(
                entry(102, UpgradeNode.ROOT, 1).withBranches(List.of(104)),
                entry(103, UpgradeNode.ROOT, 1).withBranches(List.of(104)),
                entry(104, UpgradeNode.ROOT, 1),
                entry(105, 104, 1),
                entry(106, UpgradeNode.ROOT, 1))));
        var empty = UpgradePlan.empty(catalog);
        assertEquals(List.of(catalog.node(102), catalog.node(103)), empty.newlyClosedBranches(catalog.node(104)));
        assertEquals(List.of(catalog.node(104)), empty.newlyClosedBranches(catalog.node(102)));
        assertTrue(empty.newlyClosedBranches(catalog.node(106)).isEmpty());
        var after = empty.purchase(catalog.node(103));
        assertTrue(after.newlyClosedBranches(catalog.node(102)).isEmpty());
        assertTrue(after.newlyClosedBranches(catalog.node(104)).isEmpty());
    }

    @Test
    void nodeSettings_legacyJsonShowsRequirementsAndExplicitSettingSurvivesRoundTrip() {
        var gson = new Gson();
        var legacy = gson.fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[]}", Definition.class);
        assertTrue(legacy.nodeSettings().showRequirements());
        var emptySettings = gson.fromJson("{\"nodeSettings\":{}}", Definition.class);
        assertTrue(emptySettings.nodeSettings().showRequirements());

        var settings = new NodeSettings(false);
        var configured = new Definition(10, 103, null, List.of(entry(102, UpgradeNode.ROOT, 1)),
                null, ExperimentalSettings.DEFAULT, settings);
        var reloaded = new UpgradeCatalog(gson.fromJson(gson.toJson(configured), Definition.class));
        assertEquals(settings, reloaded.definition().nodeSettings());
    }

    @ParameterizedTest
    @CsvSource({
            "-4000, true", "4000, true", "-21.125, true", "0, true",
            "-4000.25, false", "4000.25, false", "NaN, false", "Infinity, false", "-Infinity, false"
    })
    void validCoordinate_mapBounds_requireFinitePoints(float coordinate, boolean accepted) {
        assertEquals(accepted, UpgradeCatalog.validCoordinate(coordinate));
    }

    /** A new dependency with a greater ID can be purchased before its dependent. */
    @Test
    void restore_reorderedAndRewiredGraph_keepsValidPurchases() {
        var child = entry(102, 106, 2);
        var parent = entry(106, UpgradeNode.ROOT, 1);
        var catalog = new UpgradeCatalog(new Definition(12, 107, null, List.of(child, parent)));
        var plan = UpgradePlan.restore(List.of(102, 106), catalog);
        assertEquals(List.of(102, 106), plan.ids());
        assertEquals(9, plan.remaining());
    }

    /** The graph cannot contain duplicate IDs, nonexistent links, or cycles. */
    @Test
    void catalog_invalidLinks_rejectsDefinition() {
        var valid = entry(102, UpgradeNode.ROOT, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 104, null, List.of(valid, valid))));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 104, null, List.of(entry(102, 103, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 104, null, List.of(entry(102, 103, 1), entry(103, 102, 1)))));
    }

    /** Editing the amount keeps a valid purchase and uses the new modifier. */
    @Test
    void update_changedAmountAndDeletedDependency_revalidatesAllocation() {
        var speed = new UpgradeNode(2, "speed", 1, 42, Sector.CRAFTING, null, -1, -1, 100, 40, Kind.STARTER);
        var batch = new UpgradeNode(3, "batch", 2, 8, Sector.CRAFTING, Route.MASS_PRODUCTION, 2, -1, 200, 25, Kind.CHOICE);
        var catalog = new UpgradeCatalog(new Definition(8, 102, null, List.of(new Entry(speed, "", "", ""),
                new Entry(batch, "", "", ""))));
        var plan = UpgradePlan.EMPTY.purchase(UpgradeNode.SPEED).purchase(UpgradeNode.BATCH).update(catalog);
        assertEquals(42, plan.speedBonus());
        assertEquals(8, plan.effects().amount(UpgradeEffect.Kind.BATCH));
        assertEquals(5, plan.remaining());
        var newCatalog = new UpgradeCatalog(new Definition(8, 102, null, List.of(new Entry(speed, "", "", ""))));
        var revised = plan.update(newCatalog);
        assertTrue(revised.unlocked(UpgradeNode.SPEED));
        assertFalse(revised.unlocked(UpgradeNode.BATCH));
        assertEquals(7, revised.remaining());
    }

    /** Route exclusivity applies when an operator makes a passive node directly accessible. */
    @Test
    void purchase_customRouteNode_closesOpposingRoute() {
        var first = new UpgradeNode(102, "custom.102", 1, 0, Sector.CRAFTING, Route.OVERCLOCKING,
                -1, -1, 100, 40, Kind.PASSIVE);
        var other = new UpgradeNode(103, "custom.103", 1, 0, Sector.CRAFTING, Route.MASS_PRODUCTION,
                -1, -1, 180, 40, Kind.PASSIVE);
        var graph = new UpgradeCatalog(new Definition(8, 104, null, List.of(new Entry(first, "", "", ""), new Entry(other, "", "", ""))));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, UpgradePlan.empty(graph).purchase(first).availability(other));
    }

    /** Definitions without a custom path list still provide the original route locks. */
    @Test
    void decode_legacyRouteWithoutPaths_preservesExclusiveChoice() {
        var definition = UpgradeCatalog.DEFAULT.definition();
        var previousFormat = new Definition(definition.budget(), definition.nextId(), null, definition.nodes(), null);
        var graph = new UpgradeCatalog(previousFormat);
        var plan = UpgradePlan.restore(List.of(UpgradeNode.SPEED.id(), UpgradeNode.OVERDRIVE.id()), graph);
        assertEquals(Route.OVERCLOCKING.key(), plan.blockingPath(graph.node(UpgradeNode.BATCH.id())).id());
    }

    /** Paths without a shared group stay open and a purchase locks all the other members of its group. */
    @Test
    void restore_customPaths_usesOnlyTheConfiguredGroup() {
        var first = new Path("custom.first", "First route", Sector.NETWORK, "choice");
        var second = new Path("custom.second", "Second route", Sector.NETWORK, "choice");
        var third = new Path("custom.third", "Third route", Sector.NETWORK, "choice");
        var independent = new Path("custom.independent", "Independent route", Sector.NETWORK, null);
        var nodes = List.of(entryOnPath(102, first), entryOnPath(103, second), entryOnPath(104, third),
                entryOnPath(105, independent), entryOnPath(106, first));
        var paths = List.of(first, second, third, independent);
        var graph = new UpgradeCatalog(new Definition(10, 107, null, nodes, paths));
        assertEquals(paths, graph.definition().paths());
        var restored = UpgradePlan.restore(List.of(102, 105, 106), graph);
        assertEquals(List.of(102, 105, 106), restored.ids());
        assertEquals(first, restored.blockingPath(graph.node(103)));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, restored.availability(graph.node(104)));
        assertNull(restored.blockingPath(graph.node(105)));
        assertEquals(UpgradePlan.Availability.AVAILABLE, UpgradePlan.empty(graph).availability(graph.node(103)));
    }

    /** The server validates path definitions and assignments in saved documents. */
    @Test
    void catalog_invalidPathAssignments_rejectsDefinition() {
        var path = new Path("custom.first", "First route", Sector.NETWORK, null);
        var node = entryOnPath(102, path);
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 103, null, List.of(node))));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 103, null, List.of(node), List.of(path, path))));
        var wrongSector = new Path(path.id(), path.title(), Sector.CRAFTING, null);
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 103, null, List.of(node), List.of(wrongSector))));
    }

    /** An isolated node needs a link and either of the two wired prerequisites opens it. */
    @Test
    void connect_disconnectedNode_becomesPurchasableThroughEitherParent() {
        var left = entry(102, UpgradeNode.ROOT, 1);
        var right = entry(103, UpgradeNode.ROOT, 1);
        var detached = entry(104, UpgradeNode.DISCONNECTED, 1);
        var original = new UpgradeCatalog(new Definition(10, 105, null, List.of(left, right, detached)));
        assertEquals(UpgradePlan.Availability.DISCONNECTED, UpgradePlan.empty(original).availability(detached.node()));
        var primary = UpgradeCatalog.connect(List.of(left, right, detached), 102, 104);
        assertEquals(102, primary.node().previous());
        var alternate = UpgradeCatalog.connect(List.of(left, right, primary), 103, 104);
        assertEquals(103, alternate.node().alternative());
        var wired = new UpgradeCatalog(new Definition(10, 105, null, List.of(left, right, alternate)));
        assertTrue(UpgradePlan.restore(List.of(102, 104), wired).unlocked(alternate.node()));
        assertTrue(UpgradePlan.restore(List.of(103, 104), wired).unlocked(alternate.node()));
    }

    /** Connections reject existing or cyclic prerequisites before the editor saves. */
    @Test
    void connect_invalidEdge_rejectsChange() {
        var first = entry(102, UpgradeNode.ROOT, 1);
        var second = entry(103, 102, 1);
        var third = entry(104, 103, 1);
        var other = entry(105, UpgradeNode.ROOT, 1);
        var graph = List.of(first, second, third, other);
        assertThrows(IllegalArgumentException.class, () -> UpgradeCatalog.connect(graph, 102, 103));
        assertThrows(IllegalArgumentException.class, () -> UpgradeCatalog.connect(graph, 103, 103));
        assertThrows(IllegalArgumentException.class, () -> UpgradeCatalog.connect(graph, 104, 102));
        var alternate = UpgradeCatalog.connect(graph, 105, 104);
        assertThrows(IllegalArgumentException.class,
                () -> UpgradeCatalog.connect(List.of(first, second, alternate, other), UpgradeNode.ROOT, 104));
        var thirdInput = UpgradeCatalog.connect(List.of(first, second, alternate, other), 102, 104);
        assertEquals(List.of(103, 105, 102), thirdInput.node().prerequisiteIds());
        assertEquals(1, thirdInput.node().requiredCount());
    }

    @Test
    void catalog_threshold_rejectsImpossibleCountsAndInvalidReferences() {
        var first = entry(102, UpgradeNode.ROOT, 1);
        var second = entry(103, UpgradeNode.ROOT, 1);
        var join = entry(104, UpgradeNode.DISCONNECTED, 1);
        assertThrows(IllegalArgumentException.class, () -> join.node().withPrerequisites(List.of(102, 103), 0));
        assertThrows(IllegalArgumentException.class, () -> join.node().withPrerequisites(List.of(102, 103), 3));
        assertThrows(IllegalArgumentException.class, () -> join.node().withPrerequisites(List.of(102, 102), 2));
        for (var ids : List.of(List.of(102, 999), List.of(102, 104))) {
            var invalid = join.withNode(join.node().withPrerequisites(ids, 2));
            assertThrows(IllegalArgumentException.class,
                    () -> new UpgradeCatalog(new Definition(10, 105, null, List.of(first, second, invalid))));
        }
        var cyclicSecond = entry(103, 104, 1);
        var threshold = join.withNode(join.node().withPrerequisites(List.of(102, 103), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new Definition(10, 105, null, List.of(first, cyclicSecond, threshold))));
    }

    @Test
    void purchase_thresholdLosesInputsAcrossChoices_closesAtTheThreshold() {
        var first = entry(102, UpgradeNode.ROOT, 1).withBranches(List.of(104));
        var second = entry(103, UpgradeNode.ROOT, 1).withBranches(List.of(105));
        var join = entry(108, UpgradeNode.DISCONNECTED, 1);
        join = join.withNode(join.node().withPrerequisites(List.of(104, 105, 106, 107), 3));
        var catalog = new UpgradeCatalog(new Definition(10, 109, null, List.of(first, second,
                entry(104, UpgradeNode.ROOT, 1), entry(105, UpgradeNode.ROOT, 1), entry(106, UpgradeNode.ROOT, 1),
                entry(107, UpgradeNode.ROOT, 1), join)));
        var plan = UpgradePlan.empty(catalog).purchase(first.node());
        assertEquals(UpgradePlan.Availability.PREREQUISITE, plan.availability(join.node()));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, plan.purchase(second.node()).availability(join.node()));

        var combined = new UpgradeCatalog(new Definition(10, 109, null, List.of(first.withBranches(List.of(104, 105)),
                second, entry(104, UpgradeNode.ROOT, 1), entry(105, UpgradeNode.ROOT, 1),
                entry(106, UpgradeNode.ROOT, 1), entry(107, UpgradeNode.ROOT, 1), join)));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE,
                UpgradePlan.empty(combined).purchase(first.node()).availability(join.node()));
    }

    @Test
    void purchase_choiceLimit_closesUnchosenDescendantsWithNoOtherWayIn() {
        var left = entry(102, UpgradeNode.ROOT, 1);
        var right = entry(103, UpgradeNode.ROOT, 1);
        var gate = entry(104, UpgradeNode.DISCONNECTED, 1);
        gate = gate.withNode(gate.node().withPrerequisites(List.of(102, 103), 1, true));
        var closedChild = entry(105, 103, 1);
        var shared = entry(106, 103, 1);
        shared = shared.withNode(shared.node().withPrerequisites(List.of(102, 103), 1));
        var catalog = new UpgradeCatalog(new Definition(10, 107, null, List.of(left, right, gate, closedChild, shared)));
        var plan = UpgradePlan.empty(catalog).purchase(left.node());
        assertEquals(UpgradePlan.Availability.AVAILABLE, plan.availability(gate.node()));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, plan.availability(closedChild.node()));
        assertEquals(gate.node(), plan.blockingChoice(closedChild.node()));
        assertEquals(UpgradePlan.Availability.AVAILABLE, plan.availability(shared.node()));
    }

    @Test
    void purchase_thresholdWithoutBranchLimit_keepsExtraInputsOpen() {
        var left = entry(102, UpgradeNode.ROOT, 1);
        var middle = entry(103, UpgradeNode.ROOT, 1);
        var right = entry(104, UpgradeNode.ROOT, 1);
        var gate = entry(105, UpgradeNode.DISCONNECTED, 1);
        gate = gate.withNode(gate.node().withPrerequisites(List.of(102, 103, 104), 2));
        var catalog = new UpgradeCatalog(new Definition(10, 106, null, List.of(left, middle, right, gate)));
        var plan = UpgradePlan.restore(List.of(102, 103, 105), catalog);
        assertTrue(plan.unlocked(gate.node()));
        assertEquals(UpgradePlan.Availability.AVAILABLE, plan.availability(right.node()));
        assertTrue(plan.purchase(right.node()).unlocked(right.node()));
    }

    @Test
    void purchase_forkClosesBranch_butKeepsAlternativeEntrance() {
        var left = entry(102, UpgradeNode.ROOT, 1).withBranches(List.of(103));
        var right = entry(103, UpgradeNode.ROOT, 1);
        var closedChild = entry(104, 103, 1);
        var shared = UpgradeCatalog.connect(List.of(left, right, entry(105, 103, 1)), 102, 105);
        var catalog = new UpgradeCatalog(new Definition(10, 106, null, List.of(left, right, closedChild, shared)));
        var plan = UpgradePlan.restore(List.of(102), catalog);
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, plan.availability(right.node()));
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, plan.availability(closedChild.node()));
        assertEquals(UpgradePlan.Availability.AVAILABLE, plan.availability(shared.node()));
        assertEquals(left.node(), plan.blockingNode(closedChild.node()));
        var reverse = UpgradePlan.restore(List.of(103, 104), catalog);
        assertEquals(UpgradePlan.Availability.EXCLUSIVE, reverse.availability(left.node()));
    }

    @Test
    void catalog_invalidForkReferences_rejectsDraft() {
        var first = entry(102, UpgradeNode.ROOT, 1);
        var second = entry(103, UpgradeNode.ROOT, 1);
        for (var ids : List.of(List.of(102), List.of(500), List.of(103, 103))) {
            assertThrows(IllegalArgumentException.class, () -> new UpgradeCatalog(
                    new Definition(10, 104, null, List.of(first.withBranches(ids), second))));
        }
    }

    @Test
    void catalog_explicitEmptyBranches_useOnlyExplicitConflicts() {
        var first = new Entry(UpgradeNode.OVERDRIVE, "", "", "").withBranches(List.of());
        var second = new Entry(UpgradeNode.BATCH, "", "", "").withBranches(List.of());
        var catalog = new UpgradeCatalog(new Definition(10, 102, null,
                List.of(new Entry(UpgradeNode.SPEED, "", "", ""), first, second)));
        assertEquals(List.of(2, 3, 6), UpgradePlan.restore(List.of(2, 3, 6), catalog).ids());
    }

    @Test
    void catalog_nodeStylePreservesLayersAndValidatesResourcePaths() {
        var original = entry(102, UpgradeNode.ROOT, 1);
        var style = new UpgradeNodeStyle(44, UpgradeNodeStyle.Shape.FRAMELESS, "pack:textures/icon.png",
                List.of("pack:textures/base.png", "pack:textures/overlay.png"));
        var themed = new Entry(original.node(), "@custom.title", "@custom.description", "", List.of(), null, style, List.of());
        var catalog = new UpgradeCatalog(new Definition(10, 103, null, List.of(themed)));
        assertEquals(themed, catalog.entry(102));
        assertThrows(IllegalArgumentException.class, () -> new UpgradeNodeStyle(
                Float.NaN, UpgradeNodeStyle.Shape.CIRCLE, null, List.of()).validated());
        assertThrows(IllegalArgumentException.class, () -> new UpgradeNodeStyle(
                44, UpgradeNodeStyle.Shape.CIRCLE, "pack:textures/../bad.png", List.of()).validated());
    }

    /** The previous config's adjustable cap migrates and preserves purchased speed. */
    @Test
    void update_legacyOverdriveCap_migratesToEffect() {
        var legacy = UpgradeNode.all().stream().map(node -> new Entry(node, "", "", "")).toList();
        var catalog = new UpgradeCatalog(new Definition(36, 102, 24, legacy));
        var plan = UpgradePlan.EMPTY.purchase(UpgradeNode.SPEED).purchase(UpgradeNode.OVERDRIVE).update(catalog);
        assertEquals(24, plan.assemblerLimit(-1));
        assertEquals(10, plan.assemblerLimit(10));
        assertEquals(50, plan.speedBonus());
    }

    /** A node's effects can combine arbitrary supported properties and scoped bonuses. */
    @Test
    void effects_multipleModifiers_stackAndClear() {
        var row = entry(102, UpgradeNode.ROOT, 1);
        var other = entry(103, 102, 1);
        var all = UpgradeEffect.Target.ALL;
        var providers = UpgradeEffect.Target.PATTERN_PROVIDER;
        var flat = UpgradeEffect.Kind.IDLE_FLAT;
        var catalog = new UpgradeCatalog(new Definition(10, 104, null, List.of(
                new Entry(row.node(), "", "", "", List.of(new UpgradeEffect(flat, all, 5_000),
                        new UpgradeEffect(flat, providers, -30),
                        new UpgradeEffect(UpgradeEffect.Kind.SPEED_PERCENT, all, 50.5))),
                new Entry(other.node(), "", "", "", List.of(new UpgradeEffect(flat, providers, 100),
                        new UpgradeEffect(UpgradeEffect.Kind.BATCH, all, 3))))));
        var plan = UpgradePlan.restore(List.of(102, 103), catalog);
        assertEquals(5_070, plan.effects().amount(flat, providers));
        assertEquals(5_000, plan.effects().amount(flat, UpgradeEffect.Target.INTERFACE));
        assertEquals(50.5, plan.speedBonus());
        assertEquals(3, plan.effects().amount(UpgradeEffect.Kind.BATCH));
        assertEquals(0, UpgradePlan.empty(catalog).effects().amount(flat, providers));
    }

    /** An explicit empty list removes former built-in effects. */
    @Test
    void effects_explicitEmpty_doesNotRegenerateLegacyEffect() {
        var catalog = new UpgradeCatalog(new Definition(10, 102, 16,
                List.of(new Entry(UpgradeNode.CHANNELS, "", "", "", List.of()))));
        var plan = UpgradePlan.restore(List.of(0), catalog);
        assertTrue(plan.unlocked(UpgradeNode.CHANNELS));
        assertEquals(0, plan.effects().amount(UpgradeEffect.Kind.CHANNELS));
    }

    @Test
    void effects_invalidValues_respectPropertyRules() {
        var all = UpgradeEffect.Target.ALL;
        assertFalse(new UpgradeEffect(UpgradeEffect.Kind.BATCH, all, 1.5).valid());
        assertFalse(new UpgradeEffect(UpgradeEffect.Kind.IDLE_FLAT, all, Double.POSITIVE_INFINITY).valid());
        assertFalse(new UpgradeEffect(UpgradeEffect.Kind.DEVICE_CAP, all, -1).valid());
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10, 20, 30",
            "20, 10, 20, 30",
            "5, 10, 20, 25",
            "-1, 10, 10, 20",
            "-1, 10, -20, 0",
            "-1, 0, 2, 2",
            "-1, -1, 20, -1",
            "10, -1, 20, 30",
            "2147483647, -1, 20, 2147483647"
    })
    void deviceLimit_adjustsFiniteBaselineAfterApplyingCap(int base, int cap, int delta, int expected) {
        var assembler = UpgradeEffect.Target.MOLECULAR_ASSEMBLER;
        var bonus = new UpgradeEffect(UpgradeEffect.Kind.DEVICE_LIMIT, assembler, delta);
        var effects = UpgradeEffects.EMPTY.add(102, List.of(bonus));
        if (cap >= 0) {
            effects = effects.add(103, List.of(new UpgradeEffect(UpgradeEffect.Kind.DEVICE_CAP, assembler, cap)));
        }
        assertEquals(expected, effects.deviceLimit(assembler, base));
    }

    @Test
    void deviceLimit_usesStrictestBaseCapAndSumsMatchingBonuses() {
        var all = UpgradeEffect.Target.ALL;
        var assembler = UpgradeEffect.Target.MOLECULAR_ASSEMBLER;
        var cap = UpgradeEffect.Kind.DEVICE_CAP;
        var limit = UpgradeEffect.Kind.DEVICE_LIMIT;
        var effects = UpgradeEffects.EMPTY.add(102, List.of(new UpgradeEffect(cap, all, 100),
                new UpgradeEffect(cap, assembler, 16), new UpgradeEffect(cap, assembler, 20),
                new UpgradeEffect(limit, all, 2), new UpgradeEffect(limit, assembler, 4)));
        assertEquals(22, effects.deviceLimit(assembler, -1));
        assertEquals(16, effects.deviceLimit(assembler, 10));
        assertEquals(102, effects.deviceLimit(UpgradeEffect.Target.INTERFACE, -1));
    }

    /** @param id node ID
     *  @param previous prerequisite ID
     *  @param cost points to buy
     *  @return valid prototype entry except for any external reference supplied by the caller */
    private static Entry entry(int id, int previous, int cost) {
        return new Entry(new UpgradeNode(id, "custom." + id, cost, 0, Sector.NETWORK, null, previous, -1,
                100, 120, Kind.PASSIVE), "", "", "");
    }

    /** @param id stable node ID
     *  @param path custom path assigned to the new node
     *  @return a starter assigned to the custom path */
    private static Entry entryOnPath(int id, Path path) {
        return new Entry(entry(id, UpgradeNode.ROOT, 1).node(), "", "", "", List.of(), path.id());
    }
}
