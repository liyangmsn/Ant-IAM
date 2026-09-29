package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScimQuerySupportTest {

    private record Item(String userName, String email) {
    }

    private final List<Item> items = List.of(
        new Item("alice", "alice@example.com"),
        new Item("bob", "bob@corp.cn"),
        new Item("carol", null));

    @Test
    void combinesAndOrNotWithParentheses() {
        assertThat(names("userName eq \"alice\" or userName eq \"bob\"")).containsExactly("alice", "bob");
        assertThat(names("userName sw \"a\" and email ew \"example.com\"")).containsExactly("alice");
        assertThat(names("not (userName eq \"alice\") and email pr")).containsExactly("bob");
        assertThat(names("(userName eq \"alice\" or userName eq \"carol\") and not (email pr)")).containsExactly("carol");
    }

    @Test
    void keepsSpacesAndKeywordsInsideQuotedValues() {
        assertThat(names("email co \"corp or\"")).isEmpty();
        assertThat(names("email co \"@corp\"")).containsExactly("bob");
    }

    @Test
    void rejectsMalformedAndUnsupportedFilters() {
        assertThatThrownBy(() -> names("userName eq")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> names("(userName eq \"alice\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> names("userName gt \"a\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> names("title eq \"x\"")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paginatesWithOneBasedStartIndex() {
        var response = ScimQuerySupport.listResponse(items, null, 2, 1, filter -> true, (item, filter) -> true);

        assertThat(response.totalResults()).isEqualTo(3);
        assertThat(response.itemsPerPage()).isEqualTo(1);
        assertThat(response.Resources()).extracting(Item::userName).containsExactly("bob");
    }

    private List<String> names(String filter) {
        return ScimQuerySupport.listResponse(
                items,
                filter,
                null,
                null,
                leaf -> leaf.attribute().equals("userName") || leaf.attribute().equals("email"),
                (item, leaf) -> leaf.matches(leaf.attribute().equals("userName") ? item.userName() : item.email()))
            .Resources().stream()
            .map(Item::userName)
            .toList();
    }
}
