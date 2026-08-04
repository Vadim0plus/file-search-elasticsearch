package com.fileindex.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fileindex.dto.HighlightFragmentDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchServiceHighlightSplitTest {

    private static final String PRE = "";
    private static final String POST = "";

    // ElasticsearchClient is unused by splitFragment, so null is fine here.
    private final SearchService searchService = new SearchService(null);

    @Test
    void splitsFragmentIntoMatchedAndUnmatchedParts() {
        String fragment = "before " + PRE + "match" + POST + " after";

        List<HighlightFragmentDto> parts = searchService.splitFragment(fragment);

        assertThat(parts).containsExactly(
            new HighlightFragmentDto("before ", false),
            new HighlightFragmentDto("match", true),
            new HighlightFragmentDto(" after", false)
        );
    }

    @Test
    void handlesMultipleMatchesInOneFragment() {
        String fragment = PRE + "foo" + POST + " and " + PRE + "bar" + POST;

        List<HighlightFragmentDto> parts = searchService.splitFragment(fragment);

        assertThat(parts).containsExactly(
            new HighlightFragmentDto("foo", true),
            new HighlightFragmentDto(" and ", false),
            new HighlightFragmentDto("bar", true)
        );
    }

    @Test
    void returnsPlainSingleFragmentWhenNothingMatched() {
        List<HighlightFragmentDto> parts = searchService.splitFragment("no markers here");

        assertThat(parts).containsExactly(new HighlightFragmentDto("no markers here", false));
    }

    @Test
    void fileContentContainingHtmlIsNeverTurnedIntoMarkup() {
        // If a file legitimately contains "<script>...</script>", ES highlighting on the raw
        // <em>/</em> tags would let that HTML slip straight into the browser. With our sentinel
        // markers, the whole thing just becomes plain, inert text fragments.
        String fragment = "<script>alert(1)</script> " + PRE + "malicious" + POST + " tail";

        List<HighlightFragmentDto> parts = searchService.splitFragment(fragment);

        assertThat(parts).containsExactly(
            new HighlightFragmentDto("<script>alert(1)</script> ", false),
            new HighlightFragmentDto("malicious", true),
            new HighlightFragmentDto(" tail", false)
        );
        // the "<script>" text survives as inert data in an unmatched fragment - it is never
        // interpreted as markup because the caller renders fragments as escaped text, not HTML
        assertThat(parts.get(0).matched()).isFalse();
        assertThat(parts.get(0).text()).contains("<script>alert(1)</script>");
    }
}
