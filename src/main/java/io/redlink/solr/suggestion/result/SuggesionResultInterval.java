package io.redlink.solr.suggestion.result;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class SuggesionResultInterval implements SuggestionResult {

    private int limit = Integer.MAX_VALUE;
    private SuggestionRequestHandler.LimitType limitType;
    private HashMap<String, Interval> intervals = new HashMap<>();

    public SuggesionResultInterval(int limit, SuggestionRequestHandler.LimitType limitType) {
        this.limit = limit;
        this.limitType = limitType;
    }

    public Object write() {
        Map<String, Object> suggestions = new HashMap<>();

        HashMap<String, Object> suggestionIntervals = new HashMap<>();

        intervals.keySet().forEach(key -> suggestionIntervals.put(key, intervals.get(key).getFacets().write()));
        suggestions.put("suggestion_intervals", suggestionIntervals);
        return suggestions;
    }


    public void addFacet(String intervalName, String field, String value, int count, int position) {
        ((SuggesionResultSingle) intervals.get(intervalName).getFacets()).addFacet(field, value, count, position);
    }

    public void addInterval(String intervalName, LocalDateTime start, LocalDateTime end) {
        intervals.put(intervalName, new Interval(start, end, limit, limitType));
    }

    public int getCount() {
        return intervals.size();
    }

}
