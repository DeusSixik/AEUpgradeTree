package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.parameterLabel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.VBox;
import org.jetbrains.annotations.Nullable;

/**
 * Filters field rows and restores the user's collapsed groups when search is cleared.
 */
final class InspectorGroup {
    private final VBox widget = new VBox();
    private final VBox body = new VBox();
    private final VBox rows = new VBox();
    private final List<Parameter> parameters = new ArrayList<>();
    private final List<InspectorGroup> groups = new ArrayList<>();
    private final boolean hasAction;
    private final String key;
    private final String name;
    private final AeButton header;
    private boolean expanded;
    private boolean searching;

    InspectorGroup(String key, boolean expanded, @Nullable WidgetBase action) {
        this.key = key;
        this.expanded = expanded;
        hasAction = action != null;
        name = tr("editor." + key);
        widget.spacing(2).layout(style -> style.alignItems(Align.STRETCH));
        body.spacing(3).layout(style -> style.alignItems(Align.STRETCH).padding(3));
        rows.spacing(3).layout(style -> style.alignItems(Align.STRETCH));
        header = new AeButton(name);
        header.layout(style -> style.height(18).flexNone());
        header.onClick(event -> {
            boolean open = body.visibility() == Visibility.COLLAPSED;
            if (!searching) {
                this.expanded = open;
            }
            showBody(open);
        });
        widget.addChild(header);
        widget.addChild(body);
        if (action != null) {
            body.addChild(action);
        }
        body.addChild(rows);
        showBody(expanded);
    }

    VBox widget() {
        return widget;
    }

    void add(Parameter parameter) {
        insert(parameters.size(), parameter);
    }

    private void insert(int index, Parameter parameter) {
        parameters.add(index, parameter);
        rows.insertChild(index, parameter.widget());
    }

    void remove(Parameter parameter) {
        parameters.remove(parameter);
        rows.removeChild(parameter.widget());
    }

    void moveTo(Parameter parameter, InspectorGroup destination, int index) {
        remove(parameter);
        // Detach first because the two groups can be laid out in either order.
        rows.applyQueuedMutations();
        destination.insert(index, parameter);
    }

    void addGroup(InspectorGroup group) {
        groups.add(group);
        body.addChild(group.widget);
    }

    void clear() {
        parameters.clear();
        rows.clearChildren();
    }

    boolean filter(Predicate<String> matches, boolean searching, String ancestors) {
        this.searching = searching;
        String groupLabel = ancestors + " " + parameterLabel(key);
        boolean all = matches.test(groupLabel);
        boolean found = all && hasAction;
        boolean rowsVisible = false;
        for (var parameter : parameters) {
            boolean visible = parameter.available().getAsBoolean()
                    && (all || matches.test(parameter.searchText().get() + " " + groupLabel));
            parameter.widget().visibility(visible ? Visibility.VISIBLE : Visibility.COLLAPSED);
            rowsVisible |= visible;
        }
        rows.visibility(rowsVisible ? Visibility.VISIBLE : Visibility.COLLAPSED);
        found |= rowsVisible;
        for (var group : groups) {
            found |= group.filter(matches, searching, groupLabel);
        }
        widget.visibility(found ? Visibility.VISIBLE : Visibility.COLLAPSED);
        showBody(searching ? found : expanded);
        return found;
    }

    void expand() {
        expanded = true;
        showBody(true);
    }

    private void showBody(boolean open) {
        body.visibility(open ? Visibility.VISIBLE : Visibility.COLLAPSED);
        header.label((open ? "−  " : "+  ") + name);
    }

    record Parameter(WidgetBase widget, Supplier<String> searchText, BooleanSupplier available) {
        Parameter(WidgetBase widget, Supplier<String> searchText) {
            this(widget, searchText, () -> true);
        }
    }
}
