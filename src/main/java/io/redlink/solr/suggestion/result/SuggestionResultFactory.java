package io.redlink.solr.suggestion.result;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import io.redlink.solr.suggestion.service.FieldAnalyzerService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.DateMathParser;

public class SuggestionResultFactory {

    /**
     * create a multi suggestion result
     *
     * @param core
     * @param rsp
     * @param fields
     * @param query
     * @param df
     * @param limit
     * @return a multi suggestion result
     */
    public static SuggestionResult createMultiValueResult(SolrCore core, SolrQueryResponse rsp, String[] fields, String query, String df, int termLimit, int limit, SuggestionRequestHandler.LimitType limitType) {
        SuggestionResultMulti result = new SuggestionResultMulti(limit, limitType);

        SimpleOrderedMap facets = (SimpleOrderedMap) ((SimpleOrderedMap) rsp.getValues().get("facet_counts")).get("facet_fields");

        //for each word
        String[] qps = query.split("( |\\+)");
        LinkedList<List<Facet>> list_of_facet_lists = new LinkedList<>();
        for (int i = 0; i < qps.length; i++) {
            LinkedList<Facet> l = new LinkedList<>();
            list_of_facet_lists.addLast(l);
            for (String field : fields) {
                Iterator<Map.Entry> iter = ((NamedList) facets.get(field)).iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, Integer> entry = iter.next();
                    String s = " " + FieldAnalyzerService.analyzeString(core, df, entry.getKey());
                    //try if it maps to current fields
                    if (s.toLowerCase().contains(" " + qps[i].toLowerCase())) {
                        Integer count = entry.getValue();
                        if (count != null) {
                            l.addLast(new Facet(field, entry.getKey(), count));
                        }
                    }
                }
            }
        }

        if (list_of_facet_lists.isEmpty()) return result;

        getMultiSuggestions(result, list_of_facet_lists, 0, new ArrayList<Facet>());

        //SuggestionResultMulti.MultiFacet facet = result.createMultiFacet();
        //facet.add("who","Sebastian Vettel",2);
        //facet.add("who","Mark Webber",1);

        return result;
    }

    /**
     * create multi suggestions recursively
     *
     * @param result
     * @param all
     * @param i
     * @param list
     */
    private static void getMultiSuggestions(SuggestionResultMulti result, List<List<Facet>> all, int i, List<Facet> list) {
        if (i < all.size()) {
            for (Facet facet : all.get(i)) {
                List<Facet> fl = new ArrayList<Facet>(list);
                if (!fl.contains(facet)) {
                    fl.add(facet);
                }
                getMultiSuggestions(result, all, ++i, fl);
            }
        } else {
            if (!list.isEmpty()) {
                SuggestionResultMulti.MultiFacet mf = result.createMultiFacet();
                for (Facet f : list) {
                    mf.add(f.getName(), f.getValue(), f.getCount());
                }
            }
        }
    }

    /**
     * create single suggestion result
     *
     * @param core
     * @param rsp
     * @param fields
     * @param q
     * @param df
     * @param limit
     * @return a single suggestion result
     */
    public static SuggestionResult createSingleValueResult(SolrCore core, SolrQueryResponse rsp, String[] fields, String q, String op, String df, int termLimit, int limit, SuggestionRequestHandler.LimitType limitType, SuggestionRequestHandler.Strategy strategy, String suggestionField, Map<String, Map<String, Object>> intervals) {
        final SuggesionResultSingle result = new SuggesionResultSingle(limit, limitType);

        final SimpleOrderedMap facets = (SimpleOrderedMap) rsp.getValues().get("facets");

        //get results
        Pattern pattern = null;
        switch (strategy) {
            case exact:
                pattern = Pattern.compile("^" + Pattern.quote(q.trim()) + "\\S*|\\s" + Pattern.quote(q.trim()) + "\\S*");
                break;
            case permutate:
                final String split[] = q.trim().split(" |\\+");
                String w = "^";

                final int maxLength = split.length > termLimit ? termLimit : split.length;
                for (int i = 0; i < maxLength; i++) {
                    if (i > 0 && op.equals("OR")) {
                        w += "|";
                    }
                    w += "(?=.*\\b" + Pattern.quote(split[i]) + "\\S*\\b)";
                }
                pattern = Pattern.compile(w += ".+", Pattern.CASE_INSENSITIVE);
                break;
        }

        final Pattern word = pattern;
        if (intervals != null && !intervals.isEmpty()) {
            SuggesionResultInterval intervalResult = new SuggesionResultInterval(limit, limitType);
            for (String intervalName : intervals.keySet()) {
                final Date start = DateMathParser.parseMath(new Date(), (String) intervals.get(intervalName).get("start"));
                final LocalDateTime dateTimeStart = start.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();

                final Date end = DateMathParser.parseMath(new Date(), (String) intervals.get(intervalName).get("end"));
                final LocalDateTime dateTimeEnd = end.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();

                intervalResult.addInterval(intervalName, dateTimeStart, dateTimeEnd);
                final NamedList fieldFilters = (NamedList) facets.get(intervalName);
                for (String fieldName : fields) {
                    final String filterName = fieldName.concat("_filter");
                    if (fieldFilters.get(filterName) != null) {
                        final NamedList fieldResults = (NamedList) fieldFilters.get(filterName);
                        if ((Long) fieldResults.get("count") > 0) {
                            List<NamedList> fieldValues = (List) (((NamedList) fieldResults.get(fieldName)).get("buckets"));
                            fieldValues.forEach(
                                    value -> {
                                        Matcher matcher = word.matcher(FieldAnalyzerService.analyzeString(core, df, value.get("val").toString()));
                                        if (matcher.find()) {
                                            intervalResult.addFacet(intervalName, fieldName, value.get("val").toString(), ((Long) value.get("count")).intValue(), matcher.start());
                                        }
                                    }
                            );
                        }
                    }
                }
            }
            return intervalResult;
        } else {
            for (String fieldName : fields) {
                final String filterName = fieldName.concat("_filter");
                final NamedList fieldResults = (NamedList) facets.get(filterName);
                if ((Long) fieldResults.get("count") > 0) {
                    final List<NamedList> fieldValues = (List) (((NamedList) fieldResults.get(fieldName)).get("buckets"));
                    fieldValues.forEach(
                            value -> {
                                final Matcher matcher = word.matcher(FieldAnalyzerService.analyzeString(core, df, value.get("val").toString()));
                                if (matcher.find()) {
                                    result.addFacet(fieldName, value.get("val").toString(), ((Long) value.get("count")).intValue(), matcher.start());
                                }
                            }
                    );
                }
            }
        }


        return result;
    }

}
