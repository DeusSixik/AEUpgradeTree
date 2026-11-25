package dev.sixik.aeupgrade.tree;

import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.AVAILABLE;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.EXCLUSIVE;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.PREREQUISITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpgradeTreeExamplesTest {
    private static final int STARTER = UpgradeNode.all().size();

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5, 7})
    void purchase_fourWayFork_closesTheOtherThreeBranchesAndTheirChildren(int offset) {
        var catalog = UpgradeCatalog.DEFAULT;
        var plan = UpgradePlan.restore(List.of(STARTER), catalog);
        for (int choice = 1; choice <= 7; choice += 2) {
            assertEquals(AVAILABLE, plan.availability(catalog.node(STARTER + choice)));
        }
        var selected = catalog.node(STARTER + offset);
        var alternatives = new ArrayList<UpgradeNode>();
        for (int choice = 1; choice <= 7; choice += 2) {
            if (choice != offset) {
                alternatives.add(catalog.node(STARTER + choice));
            }
        }
        assertEquals(alternatives, plan.newlyClosedBranches(selected));
        assertEquals(List.of(STARTER), plan.ids());
        plan = plan.purchase(selected);
        assertTrue(plan.unlocked(selected));
        assertEquals(AVAILABLE, plan.availability(catalog.node(selected.id() + 1)));
        assertTrue(plan.newlyClosedBranches(catalog.node(selected.id() + 1)).isEmpty());
        for (int choice = 1; choice <= 7; choice += 2) {
            if (choice != offset) {
                assertEquals(EXCLUSIVE, plan.availability(catalog.node(STARTER + choice)));
                assertEquals(EXCLUSIVE, plan.availability(catalog.node(STARTER + choice + 1)));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 10, 11, 12})
    void purchase_andRestoreJunction_acceptAnyThreeInputsAndCloseTheFourth(int omitted) {
        var gson = new Gson();
        var catalog = new UpgradeCatalog(gson.fromJson(gson.toJson(UpgradeCatalog.DEFAULT.definition()),
                UpgradeCatalog.Definition.class));
        var junction = catalog.node(STARTER + 13);
        assertEquals(3, junction.requiredCount());
        assertEquals(4, junction.prerequisiteCount());
        assertTrue(junction.locksRemaining());
        var unused = catalog.node(STARTER + omitted);
        var purchases = new ArrayList<>(List.of(junction.id(), STARTER));
        var inputs = junction.prerequisiteIds().stream().filter(id -> id != STARTER + omitted).toList();
        purchases.addAll(inputs.subList(0, 2));

        var twoInputs = UpgradePlan.restore(purchases, catalog);
        assertEquals(2, twoInputs.unlockedPrerequisites(junction));
        assertEquals(PREREQUISITE, twoInputs.availability(junction));
        assertEquals(AVAILABLE, twoInputs.availability(unused));
        assertFalse(twoInputs.unlocked(junction));

        var lastInput = catalog.node(inputs.getLast());
        assertTrue(UpgradePlan.restore(List.of(STARTER, inputs.getFirst()), catalog).newlyClosedBranches(lastInput).isEmpty());
        assertEquals(List.of(unused), twoInputs.newlyClosedBranches(lastInput));
        var threeInputs = twoInputs.purchase(lastInput);
        assertEquals(AVAILABLE, threeInputs.availability(junction));
        assertEquals(EXCLUSIVE, threeInputs.availability(unused));
        assertEquals(junction, threeInputs.blockingChoice(unused));
        assertSame(threeInputs, threeInputs.purchase(unused));
        assertTrue(threeInputs.purchase(junction).unlocked(junction));
        purchases.add(inputs.getLast());
        var restored = UpgradePlan.restore(purchases, catalog);
        assertTrue(restored.unlocked(junction));
        assertEquals(EXCLUSIVE, restored.availability(unused));
        assertEquals(AVAILABLE, UpgradePlan.restore(List.of(STARTER), catalog).availability(unused));
    }

    @Test
    void restore_previousSaveWithFourInputs_keepsThreeAndTheContinuation() {
        var catalog = UpgradeCatalog.DEFAULT;
        var junction = catalog.node(STARTER + 13);
        var allPurchased = new ArrayList<>(List.of(STARTER, junction.id()));
        allPurchased.addAll(junction.prerequisiteIds().reversed());

        var restored = UpgradePlan.restore(allPurchased, catalog);
        assertEquals(3, restored.unlockedPrerequisites(junction));
        assertTrue(restored.unlocked(junction));
        assertEquals(EXCLUSIVE, restored.availability(catalog.node(junction.prerequisiteIds().getLast())));
    }
}
