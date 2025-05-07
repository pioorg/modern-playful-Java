/*
 * Copyright 2025 Piotr Przybył
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.przybyl;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnterpriseySearcherTest {

    @Test
    void testCombineUsingRRF_StandardCase() {
        // Given
        List<CatalogueItem> list1 = List.of(
            createItem("item1", "1.00", "USD"),
            createItem("item2", "2.00", "USD"),
            createItem("item3", "3.00", "USD"),
            createItem("item4", "4.00", "USD")
        );

        List<CatalogueItem> list2 = List.of(
            createItem("item3", "3.00", "USD"),
            createItem("item4", "4.00", "USD"),
            createItem("item5", "5.00", "USD"),
            createItem("item6", "6.00", "USD")
        );

        // When
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(List.of(list1, list2), 60, 10);

        // Then
        assertThat(result).hasSize(6);

        // Items 3 and 4 should be ranked higher as they appear in both lists
        assertThat(result.getFirst().filename()).isEqualTo("item3");
        assertThat(result.get(1).filename()).isEqualTo("item4");

        // Other items should follow
        assertThat(result).extracting(CatalogueItem::filename)
            .containsExactly("item3", "item4", "item1", "item2", "item5", "item6");
    }

    @Test
    void testCombineUsingRRF_EmptyLists() {
        // Given
        List<List<CatalogueItem>> emptyLists = List.of(
            Collections.emptyList(),
            Collections.emptyList()
        );

        // When
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(emptyLists, 60, 10);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCombineUsingRRF_NullInListOfLists() {
        // Given
        List<CatalogueItem> list1 = List.of(createItem("item1", "1.00", "USD"));
        List<List<CatalogueItem>> listsWithNull = new ArrayList<>();
        listsWithNull.add(list1);
        listsWithNull.add(null);

        // When/Then
        assertThrows(NullPointerException.class, () -> 
            EnterpriseySearcher.combineUsingRRF(listsWithNull, 60, 10)
        );
    }

    @Test
    void testCombineUsingRRF_TwoLists() {
        // Given
        List<CatalogueItem> list1 = List.of(
            createItem("apple", "1.00", "USD"),
            createItem("banana", "2.00", "USD")
        );

        List<CatalogueItem> list2 = List.of(
            createItem("cherry", "3.00", "USD"),
            createItem("apple", "1.00", "USD")
        );

        // When
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(List.of(list1, list2), 60, 10);

        // Then
        assertThat(result).hasSize(3);
        // Apple should be first as it appears in both lists
        assertThat(result.getFirst().filename()).isEqualTo("apple");
        // The order of the remaining items depends on their ranks in the original lists
        assertThat(result).extracting(CatalogueItem::filename)
            .containsExactlyInAnyOrder("apple", "banana", "cherry");
    }

    @Test
    void testCombineUsingRRF_ThreeLists() {
        // Given
        List<CatalogueItem> list1 = List.of(
            createItem("apple", "1.00", "USD"),
            createItem("banana", "2.00", "USD")
        );

        List<CatalogueItem> list2 = List.of(
            createItem("cherry", "3.00", "USD"),
            createItem("apple", "1.00", "USD")
        );

        List<CatalogueItem> list3 = List.of(
            createItem("banana", "2.00", "USD"),
            createItem("date", "4.00", "USD"),
            createItem("apple", "1.00", "USD")
        );

        // When
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(List.of(list1, list2, list3), 60, 10);

        // Then
        assertThat(result).hasSize(4);
        // Apple should be first as it appears in all three lists
        assertThat(result.getFirst().filename()).isEqualTo("apple");
        // Banana should be second as it appears in two lists
        assertThat(result.get(1).filename()).isEqualTo("banana");
        assertThat(result).extracting(CatalogueItem::filename)
            .containsExactly("apple", "banana", "cherry", "date");
    }

    @Test
    void testCombineUsingRRF_OneList() {
        // Given
        List<CatalogueItem> list = List.of(
            createItem("apple", "1.00", "USD"),
            createItem("banana", "2.00", "USD"),
            createItem("cherry", "3.00", "USD")
        );

        // When
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(List.of(list), 60, 10);

        // Then
        assertThat(result).hasSize(3);
        // Order should be preserved as there's only one list
        assertThat(result).extracting(CatalogueItem::filename)
            .containsExactly("apple", "banana", "cherry");
    }

    @Test
    void testCombineUsingRRF_RankWindowSize() {
        // Given
        List<CatalogueItem> list = List.of(
            createItem("apple", "1.00", "USD"),
            createItem("banana", "2.00", "USD"),
            createItem("cherry", "3.00", "USD"),
            createItem("date", "4.00", "USD"),
            createItem("elderberry", "5.00", "USD")
        );

        // When - limit to 3 results
        List<CatalogueItem> result = EnterpriseySearcher.combineUsingRRF(List.of(list), 60, 3);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(CatalogueItem::filename)
            .containsExactly("apple", "banana", "cherry");
    }

    @Test
    void testCombineUsingRRF_DifferentKValue() {
        // Given
        List<CatalogueItem> list1 = List.of(
            createItem("item1", "1.00", "EUR"),
            createItem("item2", "2.00", "EUR")
        );

        List<CatalogueItem> list2 = List.of(
            createItem("item2", "2.00", "EUR"),
            createItem("item3", "3.00", "EUR")
        );

        // When - with k=1 (higher impact of rank)
        List<CatalogueItem> resultK1 = EnterpriseySearcher.combineUsingRRF(List.of(list1, list2), 1, 10);

        // When - with k=100 (lower impact of rank)
        List<CatalogueItem> resultK100 = EnterpriseySearcher.combineUsingRRF(List.of(list1, list2), 100, 10);

        // Then - both should have item2 first (as it appears in both lists)
        assertThat(resultK1.getFirst().filename()).isEqualTo("item2");
        assertThat(resultK100.getFirst().filename()).isEqualTo("item2");

        // But the ordering of the remaining items might differ due to different k values
        // We don't assert on the exact ordering as it depends on the implementation details
    }

    private CatalogueItem createItem(String name, String price, String currency) {
        return new CatalogueItem(
            name, 
            "path/to/" + name, 
            new Price(new BigDecimal(price), currency)
        );
    }
}
