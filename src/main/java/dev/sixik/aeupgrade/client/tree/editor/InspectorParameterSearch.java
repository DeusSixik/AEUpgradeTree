package dev.sixik.aeupgrade.client.tree.editor;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Matches each word of an inspector query against a parameter or group caption.
 */
final class InspectorParameterSearch {
    private InspectorParameterSearch() {
    }

    static Predicate<String> compile(String query) {
        if (query.isBlank()) {
            return text -> true;
        }
        var words = normalize(query.strip()).split("\\s+");
        return text -> {
            String caption = normalize(text);
            for (var word : words) {
                if (!caption.contains(word)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('−', '-');
    }
}
