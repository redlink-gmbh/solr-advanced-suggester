package io.redlink.solr.suggestion.result;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import io.redlink.solr.suggestion.params.SuggestionResultParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.apache.solr.common.util.NamedList;


public class SuggesionResultSingle implements SuggestionResult {

    private int count = 0;
    private int limit = Integer.MAX_VALUE;
    private SuggestionRequestHandler.LimitType limitType;
    private HashMap<String, List<Facet>> fields = new HashMap<>();

    private static final Comparator<Facet> COUNT_SORTER = Comparator.naturalOrder();

    public SuggesionResultSingle(int limit, SuggestionRequestHandler.LimitType limitType) {
        this.limit = limit;
        this.limitType = limitType;
    }

    public Object write() {
        NamedList<Object> suggestions = new NamedList<>();

        NamedList<Object> suggestionFacets = new NamedList<>();

        //sort results
        for (String field : fields.keySet()) {
            fields.get(field).sort(COUNT_SORTER);
        }

        //crop results
        if (limit < Integer.MAX_VALUE) {
            cropResult();
        }

        //put results in result structure
        for (String field : fields.keySet()) {
            NamedList facets = new NamedList();

            for (Facet facet : fields.get(field)) {
                facets.add(facet.value, facet.count);
                count++;
            }

            suggestionFacets.add(field, facets);
        }

        suggestions.add(SuggestionResultParams.SUGGESTION_COUNT, count);

        if (count > 0) {
            suggestions.add(SuggestionResultParams.SUGGESTION_FACETS, suggestionFacets);
        }

        return suggestions;
    }

    /**
     * crop to limit
     */
    @SuppressWarnings("java:S3776")
    private void cropResult() {
        if (limitType == SuggestionRequestHandler.LimitType.each) {
            for (String field : fields.keySet()) {
                if (fields.get(field).size() > limit) {
                    fields.put(field, fields.get(field).subList(0, limit));
                }
            }
        } else {
            HashMap<String, List<Facet>> facetsMap = new HashMap<>();
            boolean more = true;
            int number = 0;
            int c = 0;

            while (c < limit && more) {
                more = false;
                for (String field : fields.keySet()) {
                    if (fields.get(field).size() > number) {
                        more = true;
                        c++;
                        facetsMap.computeIfAbsent(field, k -> new ArrayList<>())
                                .add(fields.get(field).get(number));
                    }
                    if (c == limit) break;
                }
                number++;
            }

            fields = facetsMap;
        }
    }

    public void addFacet(String field, String value, int count, int position) {
        fields.computeIfAbsent(field, k -> new ArrayList<>())
                .add(new Facet(value, count, position));
    }

    public int getCount() {
        return fields.size();
    }

    static class Facet implements Comparable<Facet> {

        int position;
        String value;
        int count;

        Facet(String value, int count, int position) {
            this.value = value;
            this.count = count;
            this.position = position;
        }

        @Override
        public int compareTo(Facet facet) {
            return position == facet.position ? Integer.compare(facet.count, count) : Integer.compare(position, facet.position);
        }
    }

}
