package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InspectorParameterSearchTest {
    @ParameterizedTest
    @CsvSource({
            "' idle   DRAW ', 'Idle draw (AE/t) Effects', true",
            "'effects energy assemblers -25', 'Effects Energy Idle draw (%) Assemblers -25', true",
            "'idle -15%', 'Idle draw −15%', true",
            "'energy assemblers', 'Effects Energy Idle draw (%) Pattern providers -25', false",
            "'ее', 'Ещё эффекты', false",
            "ещё, 'Еще эффекты', true",
            "'фоны PNG', 'PNG Фоны Внешний вид', true",
            "'фоны форма', 'Фоны Внешний вид', false",
            "'  ', 'Стоимость', true"
    })
    void search_parameterLabels_requireEveryWordIgnoringCase(String query, String label, boolean match) {
        assertEquals(match, InspectorParameterSearch.compile(query).test(label));
    }
}
