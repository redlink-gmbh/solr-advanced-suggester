package io.redlink.solr.suggestion.service;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import io.redlink.solr.suggestion.jsonfacetmodel.Pivot;
import io.redlink.solr.suggestion.params.SuggestionRequestParams;
import io.redlink.solr.suggestion.params.SuggestionResultParams;
import io.redlink.solr.suggestion.result.SuggesionResultSingle;
import io.redlink.solr.suggestion.result.SuggestionResult;
import io.redlink.solr.suggestion.result.SuggestionResultFactory;
import io.redlink.solr.suggestion.result.SuggestionResultMulti;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.parser.QueryParser;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This suggestion service queries a given core for facet suggestions based on an input string
 * <p/>
 * Author: Thomas Kurz
 * Author: Alfonso Noriega Meneses
 */
public class SuggestionService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String ARG_FIELD_DEFAULTS = "defaults";
    private static final String RSP_FIELD_SPELLCHECK = "spellcheck";
    private static final String RSP_FIELD_FACET = "facet";

    private static final String IGNORE_CASE_REGEX = "[%s|%s]";
    private static final String PREFIX_REGEX = "/((.*[^A-Za-z0-9_])?%s.*)/";
    private static final String INTERVAL_QUERY = "%s:[%s TO %s]";
    //LUCENE regex list of special characters
    private static final Collection<String> SOLR_REGEX_ESCAPE_CHARS =
            Arrays.asList("-", ".", "*", "+", "&&", "||", "!", "(", ")",
                    "{", "}", "[", "]", "^", "\"", "~", "*", "?", ":", "\\", "/");

    private String internalFacetLimit = "50";

    private boolean spellCheckEnabled = false;

    private SolrCore solrCore;

    private SearchHandler searchHandler;

    public SuggestionService(SolrCore solrCore, NamedList args) {

        final NamedList l = new NamedList();

        //set spellcheck component if there is one
        if (((ArrayList) args.get("first-components")).contains("suggestionSpellcheck")) {
            final List component = new ArrayList<String>();
            component.add("suggestionSpellcheck");
            l.add("first-components", component);
            spellCheckEnabled = true;
        }

        if (args.get(ARG_FIELD_DEFAULTS) != null && ((NamedList) args.get(ARG_FIELD_DEFAULTS)).get(SuggestionRequestParams.SUGGESTION_INTERNAL_LIMIT) != null) {
            internalFacetLimit = (String) ((NamedList) args.get(ARG_FIELD_DEFAULTS)).get(SuggestionRequestParams.SUGGESTION_INTERNAL_LIMIT);
        }

        this.solrCore = solrCore;
        this.searchHandler = new SearchHandler();
        this.searchHandler.init(l);
        this.searchHandler.inform(solrCore);
    }

    @SuppressWarnings("java:S107")
    public void run(SolrQueryResponse rsp, SolrParams params, String query, String df, String[] fields, String[] singleValueFields, String[] multiValueFields, String[] fqs, int termLimit, int limit, SuggestionRequestHandler.LimitType limitType, SuggestionRequestHandler.Type type, SuggestionRequestHandler.Strategy strategy, String suggestionField, Map<String, Map<String, Object>> intervals) throws Exception {

        //analyze query in advance
        final String analyzedQuery = FieldAnalyzerService.analyzeString(solrCore, df, query);
        final SolrQueryResponse response = query(analyzedQuery, params, df, fields, fqs, termLimit, suggestionField, intervals);

        //In case of non default operator set default operator by configuration or parameters
        String operator = "AND";
        if (Objects.nonNull(params.get("q.op"))) {
            operator = params.get("q.op");
        }

        //Create the suggestion results
        SuggestionResult[] result;
        result = this.getSuggestionResults(analyzedQuery, operator, df, singleValueFields, multiValueFields, termLimit, limit, limitType, type, strategy, suggestionField, intervals, response);

        //if no results, try spellchecker (if defined and if spellchecked query differs from original)
        if ((Objects.isNull(result) && spellCheckEnabled)) {
            final String spellCheckedQuery = getSpellCheckedQuery(response);
            final Object spellCheckResult = response.getValues().get(RSP_FIELD_SPELLCHECK);

            //query with checked query
            if (spellCheckedQuery != null && !analyzedQuery.equals(spellCheckedQuery)) {
                final SolrQueryResponse spellCheckedResponse = query(spellCheckedQuery, params, df, fields, fqs, termLimit, suggestionField, intervals);
                result = this.getSuggestionResults(spellCheckedQuery, operator, df, singleValueFields, multiValueFields, termLimit, limit, limitType, type, strategy, suggestionField, intervals, spellCheckedResponse);
                //add result of spellchecker component
                if (spellCheckResult != null && result != null) {
                    //TODO remove * on last position of collation
                    rsp.add(RSP_FIELD_SPELLCHECK, spellCheckResult);
                }
            }
        }

        if (Objects.isNull(result)) {
            result = createEmptyResults(type, limit, limitType);
        }

        if (result[0] != null) {
            rsp.add(SuggestionResultParams.SUGGESTIONS, result[0].write());
        }
        if (result[1] != null) {
            rsp.add(SuggestionResultParams.MULTI_SUGGESTIONS, result[1].write());
        }
    }

    @SuppressWarnings("java:S107")
    private SuggestionResult[] getSuggestionResults(String query, String op, String df, String[] singleValueFields, String[] multiValueFields, int termLimit, int limit, SuggestionRequestHandler.LimitType limitType, SuggestionRequestHandler.Type type, SuggestionRequestHandler.Strategy strategy, String suggestionField, Map<String, Map<String, Object>> intervals, SolrQueryResponse response) {
        final SuggestionResult[] result;
        if (response.getValues().get("facets") instanceof SimpleOrderedMap facets) {
            if ((Long) facets.get("count") > 0) {
                result = createResults(response, singleValueFields, multiValueFields, query, op, df, type, termLimit, limit, limitType, strategy, suggestionField, intervals);
            } else {
                result = null;
            }
        } else {
            final SolrDocumentList facets = (SolrDocumentList) response.getValues().get(RSP_FIELD_FACET);
            if (facets.getNumFound() > 0) {
                result = createResults(response, singleValueFields, multiValueFields, query, op, df, type, termLimit, limit, limitType, strategy, suggestionField, intervals);
            } else {
                result = null;
            }
        }
        return result;
    }

    private SuggestionResult[] createEmptyResults(SuggestionRequestHandler.Type type, int limit, SuggestionRequestHandler.LimitType limitType) {
        final SuggestionResult[] result = new SuggestionResult[2];
        switch (type) {
            case single:
                result[0] = new SuggesionResultSingle(limit, limitType);
                break;
            case multi:
                result[1] = new SuggestionResultMulti(limit, limitType);
                break;
            case mixed:
                result[0] = new SuggesionResultSingle(limit, limitType);
                result[1] = new SuggestionResultMulti(limit, limitType);
        }
        return result;
    }

    @SuppressWarnings("java:S107")
    protected SuggestionResult[] createResults(SolrQueryResponse rsp, String[] singleValueFields, String[] multiValueFields, String query, String op, String df, SuggestionRequestHandler.Type type, int termLimit, int limit, SuggestionRequestHandler.LimitType limitType, SuggestionRequestHandler.Strategy strategy, String sugestionField, Map<String, Map<String, Object>> intervals) {
        SuggestionResult[] result = new SuggestionResult[2];
        switch (type) {
            case single:
                result[0] = SuggestionResultFactory.createSingleValueResult(solrCore, rsp, singleValueFields, query, op, df, termLimit, limit, limitType, strategy, sugestionField, intervals);
                break;
            case multi:
                //TODO consider strategy
                result[1] = SuggestionResultFactory.createMultiValueResult(solrCore, rsp, multiValueFields, query, df, termLimit, limit, limitType);
                break;
            case mixed:
                result[0] = SuggestionResultFactory.createSingleValueResult(solrCore, rsp, singleValueFields, query, op, df, termLimit, limit, limitType, strategy, sugestionField, intervals);
                result[1] = SuggestionResultFactory.createMultiValueResult(solrCore, rsp, multiValueFields, query, df, termLimit, limit, limitType);
        }

        if ((result[0] == null || result[0].getCount() == 0) && (result[1] == null || ((SuggestionResultMulti) result[1]).getCount() == 0)) {
            result = null;
        }
        return result;
    }

    private String getSpellCheckedQuery(SolrQueryResponse rsp) {

        //check if spellcheck result exists.
        if (rsp.getValues().get(RSP_FIELD_SPELLCHECK) == null) {
            return null;
        }

        final NamedList collations = (NamedList) ((NamedList) rsp.getValues().get(RSP_FIELD_SPELLCHECK)).get("collations");

        if (collations != null && collations.size() > 0) {
            return (String) collations.get("collation");
        } else {
            return (String) ((NamedList) ((NamedList) rsp.getValues().get(RSP_FIELD_SPELLCHECK)).get("suggestions")).get("collation");
        }
    }

    @SuppressWarnings({"java:S3776", "java:S107"})
    private SolrQueryResponse query(String query, SolrParams originalParams, String df, String[] fields, String[] fqs, int termLimit, String suggestionField, Map<String, Map<String, Object>> intervals) throws Exception {

        final SolrQueryResponse rsp = new SolrQueryResponse();

        //remove lucene escaping character
        query = unescapeQuery(query);

        //remove *
        if (query.endsWith("*")) {
            query = query.substring(0, query.length() - 1);
        }


        //Split the query into terms separated by spaces
        List<String> terms = Arrays.asList(query.trim().split("[ +]"));

        //Check if the number of terms in the query is bigger than the suggestion.term.limit
        if (terms.size() > termLimit) {
            terms = terms.subList(0, termLimit);
        }

        //Get the REGEX expression for each term to make them match as prefix in any word of a field.
        final List<String> queryPrefixes = terms.stream()
                .map(QueryParser::escape)
                .map(term -> term.chars()
                        .mapToObj(i -> (char) i)
                        .map(letter -> {
                            if (Character.isAlphabetic(letter)) {
                                return String.format(IGNORE_CASE_REGEX, letter, StringUtils.upperCase(letter.toString()));
                            } else {
                                return letter.toString();
                            }
                        })
                        .collect(Collectors.joining()))
                .map(prefix -> String.format(PREFIX_REGEX, prefix))
                .toList();

        log.debug("original query params: {}", originalParams);

        //Prepare query
        final ModifiableSolrParams params = new ModifiableSolrParams();

        //add original params
        params.add(originalParams);

        //add other params
        final SolrQueryRequest req = new LocalSolrQueryRequest(solrCore, params);
        params.set(CommonParams.Q, "*:*");
        params.set(CommonParams.DF, df);

        //In case of non default operator set default operator by configuration or parameters
        params.set("q.op", "AND");
        if (Objects.nonNull(originalParams.get("q.op"))) {
            params.set("q.op", originalParams.get("q.op"));
        }

        params.set(FacetParams.FACET, "true");

        //clean param lists
        params.remove(FacetParams.FACET_FIELD);
        params.remove(CommonParams.FQ);
        params.remove(RSP_FIELD_SPELLCHECK);
        params.remove("spellcheck.collate");

        final ArrayList<String> queryRegex = new ArrayList<>();
        final Map<String, Object> filterMapNamed = new HashMap<>();

        //////////////////
        //Filed facets definition
        //////////////////
        for (String field : fields) {
            //preparing the prefix query for each search term
            final List<String> fieldQuery = queryPrefixes.stream()
                    .map(prefix -> String.join(":", field, prefix))
                    .toList();

            //Joining different search terms regex
            final String fieldQueryRegex = String.join(" ", fieldQuery);

            //Settings for the field term facet
            final Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("type", Pivot.facetType.terms.name());
            fieldMap.put("field", field);
            fieldMap.put("limit", Integer.valueOf(internalFacetLimit));
            fieldMap.put("mincount", 1);

            final Map<String, Object> fieldMapNamed = new HashMap<>();
            fieldMapNamed.put(field, fieldMap);

            //Settings for the field filter facet
            final Map<String, Object> filterMap = new HashMap<>();
            filterMap.put("type", Pivot.facetType.query.name());
            filterMap.put("q", fieldQueryRegex);
            filterMap.put(RSP_FIELD_FACET, fieldMapNamed);

            filterMapNamed.put(field.concat("_filter"), filterMap);

            queryRegex.add(fieldQueryRegex);
        }

        //////////////////
        //Adding Intervals
        //////////////////
        final Map<String, Object> intervalsJson = new HashMap<>();
        if (originalParams.getBool(SuggestionRequestParams.SUGGESTION_INTERVAL, false) && !intervals.isEmpty()) {
            intervals.keySet().forEach(intervalKey -> {
                final Map<String, Object> intervalMap = intervals.get(intervalKey);
                final Map<String, Object> intervalJson = new HashMap<>();
                intervalJson.put("type", Pivot.facetType.query.name());
                intervalJson.put("q", String.format(INTERVAL_QUERY, suggestionField, intervalMap.get("start"), intervalMap.get("end")));
                intervalJson.put(RSP_FIELD_FACET, filterMapNamed);

                intervalsJson.put(intervalKey, intervalJson);
            });
        }

        //Filtering here provides smaller response but takes more time, better skip this general query
        //final String prefixSolrQuery = String.join(" OR ", queryRegex.stream().map(term -> "("+term+")").collect(Collectors.toList()));
        //params.set(CommonParams.Q, prefixSolrQuery);

        //Transcend previous filter queries
        if (fqs != null) {
            for (String fq : fqs) {
                params.add(CommonParams.FQ, fq);
            }
        }

        if (spellCheckEnabled) {
            params.add(RSP_FIELD_SPELLCHECK, "true");

            final String spellcheckQuery = String.join(" ", terms);
            //QueryParser.escape(String.join(" ", terms).concat("*"));
            params.add("spellcheck.q", spellcheckQuery);
            params.add("spellcheck.collate", "true");
            final String accuracy = originalParams.get("spellcheck.accuracy");
            if (Objects.nonNull(accuracy)) {
                params.add("spellcheck.accuracy", accuracy);
            }
        }

        final Map<String, Object> jsonFacet = new HashMap<>();
        if (originalParams.getBool(SuggestionRequestParams.SUGGESTION_INTERVAL, false) && !intervalsJson.isEmpty()) {
            jsonFacet.put(RSP_FIELD_FACET, intervalsJson);
            req.setJSON(jsonFacet);
        } else {
            jsonFacet.put(RSP_FIELD_FACET, filterMapNamed);
            req.setJSON(jsonFacet);
        }

        try {
            log.debug("internal request: {}", req);
            log.debug("JSON facet query: {}", req.getJSON());
            //execute query and return
            final long millis = System.currentTimeMillis();
            searchHandler.handleRequestBody(req, rsp);
            log.info("Internal query for suggestions took a Total time of: {}ms", System.currentTimeMillis() - millis);
            log.debug("internal response: {}", rsp.getValues());
            return rsp;
        } catch (SolrException e) {
            log.error("Solr server exception while handling suggestion request (code {}): {}", e.code(), e.getMessage(), e);
            throw e;
        } catch (final InterruptedException e) {
            //Do not wrap interrupted exceptions!
            log.error("The process has been interrupted: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Exception while handling suggestion request: {}", e.getMessage(), e);
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "internal server error", e);
        } finally {
            req.close();
        }
    }

    private String unescapeQuery(String query) {
        final StringBuilder sb = new StringBuilder();

        int i = 0;
        while (i < query.length()) {
            char c = query.charAt(i);
            if (c == '\\' && i < query.length() - 1) {
                if (SOLR_REGEX_ESCAPE_CHARS.contains(String.valueOf(query.charAt(i + 1)))) {
                    sb.append(query.charAt(i + 1));
                    ++i;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
            ++i;
        }

        return sb.toString();
    }

}
