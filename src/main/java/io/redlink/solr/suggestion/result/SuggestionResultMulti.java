package io.redlink.solr.suggestion.result;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import io.redlink.solr.suggestion.params.SuggestionResultParams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.common.util.NamedList;

public class SuggestionResultMulti implements SuggestionResult {

    private int limit = Integer.MAX_VALUE;
    private SuggestionRequestHandler.LimitType limitType;
    private List<MultiFacet> suggestionList = new ArrayList<>();

    public SuggestionResultMulti(int limit, SuggestionRequestHandler.LimitType limitType) {
        this.limitType = limitType;
        this.limit = limit;
    }

    public int getCount() {
        return suggestionList.stream().mapToInt(suggestion -> suggestion.count).sum();
    }

    @Override
    public Object write() {
        Map<String, Object> suggestionResult = new HashMap<>();

        //sort results
        suggestionList.sort((mf1, mf2) -> Integer.compare(mf2.count, mf1.count));

        //Crop results
        //TODO use limitType
        if (limit < Integer.MAX_VALUE && limit < suggestionList.size()) {
            suggestionList = suggestionList.subList(0, limit);
        }

        suggestionResult.put(SuggestionResultParams.SUGGESTION_COUNT, suggestionList.size());

        NamedList<Object> suggestions = new NamedList<>();

        for (MultiFacet mf : suggestionList) {
            suggestions.add(mf.name.toLowerCase(), mf.write());
        }

        suggestionResult.put(SuggestionResultParams.SUGGESTION_FACETS, suggestions);
        return suggestionResult;
    }

    static class MultiFacet {

        HashMap<String, HashMap<String, Integer>> facets = new HashMap<>();
        String name;
        Integer count = Integer.MAX_VALUE;

        public void add(final String name, final String value, Integer count) {
            facets.computeIfAbsent(name, k -> new HashMap<>())
                    .put(value, count);
            this.name = this.name == null ? value : this.name + " " + value;
            this.count = Math.min(this.count, count);
        }

        public Map<String, Object> write() {

            HashMap<String, Object> out = new HashMap<>();

            out.put("count", count);
            out.put("facets", facets);

            return out;
        }

    }

    MultiFacet createMultiFacet() {
        MultiFacet m = new MultiFacet();
        suggestionList.add(m);
        return m;
    }
}
