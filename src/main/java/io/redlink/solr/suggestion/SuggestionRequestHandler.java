package io.redlink.solr.suggestion;

import io.redlink.solr.suggestion.params.SuggestionRequestParams;
import io.redlink.solr.suggestion.service.SuggestionService;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S115")
public class SuggestionRequestHandler extends SearchHandler implements SolrCoreAware {

    private static final String DEFAULT_END_VALUE = "NOW/DAY";
    private static final double DEFAULT_SCORE_VALUE = 1D;

    private static final String RESPONSE_FIELD_ERROR = "error";
    private static final String RESPONSE_FIELD_WARNING = "warning";

    public enum Type {
        single,
        multi,
        mixed;

        public static Type parse(String s, Type def) {
            if (s == null) {
                return def;
            }
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    public enum Strategy {
        exact,
        permutate;

        public static Strategy parse(String s, Strategy def) {
            if (s == null) {
                return def;
            }
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    public enum LimitType {
        all,
        each;

        public static LimitType parse(String s, LimitType def) {
            if (s == null) {
                return def;
            }
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionRequestHandler.class);

    private SuggestionService suggestionService;

    private Strategy strategy = Strategy.permutate;
    private boolean suggestion = true;
    private String df = null;
    private String[] fields = null;
    private String[] multivalueFields = null;
    private String[] fqs = null;
    private int termLimit = 10;
    private int limit = Integer.MAX_VALUE;
    private LimitType limitType = LimitType.all;

    private boolean suggestionInterval = false;
    private boolean suggestionIntervalOther = false;

    @Override
    public void inform(SolrCore core) {
        super.inform(core);
        suggestionService = new SuggestionService(core, this.getInitArgs());

        //set default args
        NamedList args = (NamedList) this.getInitArgs().get("defaults");

        suggestion = args.get(SuggestionRequestParams.SUGGESTION) != null ?
                Boolean.parseBoolean((String) args.get(SuggestionRequestParams.SUGGESTION)) : suggestion;
        termLimit = args.get(SuggestionRequestParams.SUGGESTION_TERM_LIMIT) != null ?
                Integer.parseInt((String) args.get(SuggestionRequestParams.SUGGESTION_TERM_LIMIT)) : termLimit;

        limit = args.get(SuggestionRequestParams.SUGGESTION_LIMIT) != null ?
                Integer.parseInt((String) args.get(SuggestionRequestParams.SUGGESTION_LIMIT)) : limit;

        limitType = args.get(SuggestionRequestParams.SUGGESTION_LIMIT_TYPE) != null ?
                LimitType.parse((String) args.get(SuggestionRequestParams.SUGGESTION_LIMIT_TYPE), limitType) : limitType;

        df = args.get(SuggestionRequestParams.SUGGESTION_DF) != null ?
                (String) args.get(SuggestionRequestParams.SUGGESTION_DF) : df;

        strategy = args.get(SuggestionRequestParams.SUGGESTION_STRATEGY) != null ?
                Strategy.parse((String) args.get(SuggestionRequestParams.SUGGESTION_STRATEGY), strategy) : strategy;

        List<String> argFields = args.getAll(SuggestionRequestParams.SUGGESTION_FIELD) != null ?
                args.getAll(SuggestionRequestParams.SUGGESTION_FIELD) : Collections.emptyList();
        if (!argFields.isEmpty()) {
            this.fields = argFields.toArray(new String[0]);
        }

        List<String> argMultivalueFields = args.getAll(SuggestionRequestParams.SUGGESTION_MULTIVALUE_FIELD) != null ?
                args.getAll(SuggestionRequestParams.SUGGESTION_MULTIVALUE_FIELD) : Collections.emptyList();
        if (!argMultivalueFields.isEmpty()) {
            this.multivalueFields = argFields.toArray(new String[argMultivalueFields.size()]);
        }

        List<String> argFqs = args.getAll(CommonParams.FQ) != null ?
                args.getAll(CommonParams.FQ) : Collections.emptyList();
        if (!argFqs.isEmpty()) {
            this.fqs = argFqs.toArray(new String[argFields.size()]);
        }

    }

    @Override
    @SuppressWarnings("java:S3776")
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {

        final SolrParams params = req.getParams();

        if (!params.getBool(SuggestionRequestParams.SUGGESTION, suggestion)) {
            super.handleRequestBody(req, rsp);
            return;
        }

        String q = params.get(CommonParams.Q);
        if (q == null) {
            rsp.add(RESPONSE_FIELD_ERROR, error(400, "SuggestionRequest needs to have a 'q' parameter"));
            return;
        }

        String[] paramSingleFields = params.getParams(SuggestionRequestParams.SUGGESTION_FIELD) != null ? params.getParams(SuggestionRequestParams.SUGGESTION_FIELD) : fields;

        String[] paramMultivalueFields = params.getParams(SuggestionRequestParams.SUGGESTION_MULTIVALUE_FIELD) != null ? params.getParams(SuggestionRequestParams.SUGGESTION_MULTIVALUE_FIELD) : multivalueFields;

        if (paramSingleFields == null && paramMultivalueFields == null) {
            rsp.add(RESPONSE_FIELD_ERROR, error(400, "SuggestionRequest needs to have at least one 'suggestion.field' parameter or one 'suggestion.multivalue.field' parameter defined."));
            return;
        }

        int paramTermLimit = params.getInt(SuggestionRequestParams.SUGGESTION_TERM_LIMIT, this.termLimit);
        if (paramTermLimit < 1) {
            rsp.add(RESPONSE_FIELD_ERROR, error(400, "SuggestionRequest needs to have a 'suggestion.term.limit' greater than 0"));
            return;
        }

        int paramLimit = params.getInt(SuggestionRequestParams.SUGGESTION_LIMIT, this.limit);
        if (paramLimit < 1) {
            rsp.add(RESPONSE_FIELD_ERROR, error(400, "SuggestionRequest needs to have a 'suggestion.limit' greater than 0"));
            return;
        }

        String paramDf = params.get(SuggestionRequestParams.SUGGESTION_DF, this.df);
        if (paramDf == null) {
            rsp.add(RESPONSE_FIELD_ERROR, error(400, "SuggestionRequest needs to have a 'df' parameter"));
            return;
        }

        final Strategy paramStrategy = Strategy.parse(params.get(SuggestionRequestParams.SUGGESTION_STRATEGY, null), this.strategy);

        final LimitType paramLimitType = LimitType.parse(params.get(SuggestionRequestParams.SUGGESTION_LIMIT_TYPE, null), this.limitType);

        final String[] paramFqs = params.getParams(CommonParams.FQ) != null ? params.getParams(CommonParams.FQ) : this.fqs;

        final Type type;
        if (paramSingleFields != null && paramMultivalueFields == null) {
            type = Type.single;
        } else if (paramSingleFields == null) {
            type = Type.multi;
            rsp.add(RESPONSE_FIELD_WARNING, error(410, "Multivalue suggestions are deprecated and will not be supported in further versions"));
        } else {
            type = Type.mixed;
            rsp.add(RESPONSE_FIELD_WARNING, error(410, "Multivalue suggestions are deprecated and will not be supported in further versions"));
        }

        final String[] allFields = ArrayUtils.addAll(paramSingleFields, paramMultivalueFields);

        ///////////////////////
        //Suggestion Intervals
        ///////////////////////
        final Map<String, Map<String, Object>> rangesMap = new HashMap<>();
        final String intervalField = params.get(SuggestionRequestParams.SUGGESTION_INTERVAL_FIELD);

        if (params.getBool(SuggestionRequestParams.SUGGESTION_INTERVAL, suggestionInterval)) {
            final String[] ranges = params.getParams(SuggestionRequestParams.SUGGESTION_INTERVAL_LABEL);
            if (ranges == null || ranges.length <= 0) {
                rsp.add(RESPONSE_FIELD_ERROR, error(400,
                        "SuggestionRequest needs to have at least one '" + SuggestionRequestParams.SUGGESTION_INTERVAL_LABEL + "' parameter to create intervals"));
                return;
            }

            if (StringUtils.isEmpty(intervalField)) {
                rsp.add(RESPONSE_FIELD_ERROR, error(400,
                        "SuggestionRequest needs to have a '" + SuggestionRequestParams.SUGGESTION_INTERVAL_FIELD + "' parameter to create intervals"));
                return;
            }

            final Boolean other = params.getBool(SuggestionRequestParams.SUGGESTION_INTERVAL_OTHER, suggestionIntervalOther);

            for (int i = 0; i < ranges.length; i++) {
                final String label = ranges[i];
                final Map<String, Object> rangeConfigurations = new HashMap<>();

                final String rangeStartParam = String.format(SuggestionRequestParams.SUGGESTION_INTERVAL_RANGE_START, label);
                final Object startValue = params.get(rangeStartParam);
                if (startValue == null) {
                    rsp.add(RESPONSE_FIELD_ERROR, error(400,
                            "SuggestionRequest needs to have a '" + String.format(SuggestionRequestParams.SUGGESTION_INTERVAL_RANGE_START, label) + "' parameter to create an interval"));
                    return;
                }
                rangeConfigurations.put("start", startValue);

                final String rangeEndParam = String.format(SuggestionRequestParams.SUGGESTION_INTERVAL_RANGE_END, label);
                final Object endValue = params.get(rangeEndParam, DEFAULT_END_VALUE);
                rangeConfigurations.put("end", endValue);

                final String rangeScoreParam = String.format(SuggestionRequestParams.SUGGESTION_INTERVAL_RANGE_SCORE, label);
                double scoreValue = params.getDouble(rangeScoreParam, DEFAULT_SCORE_VALUE);
                rangeConfigurations.put("score", scoreValue);

                rangesMap.put(label, rangeConfigurations);
            }

        }

        LOGGER.debug("Get suggestions for query '{}', type: {}, fqs: {}", q, type, paramFqs != null ? StringUtils.join(paramFqs, ",") : "none");
        suggestionService.run(rsp, params, q, paramDf, allFields, paramSingleFields, paramMultivalueFields, paramFqs, paramTermLimit, paramLimit, paramLimitType, type, paramStrategy, intervalField, rangesMap);
    }

    private HashMap<String, Object> error(int code, String msg) {
        final HashMap<String, Object> error = new HashMap<>();
        error.put("msg", msg);
        error.put("code", code);
        return error;
    }


    @Override
    public String getDescription() {
        return "This handler creates suggestions for a faceted search";
    }

    public String getSource() {
        return "no source";
    }

    public String getVersion() {
        return "1.0-SNAPSHOT";
    }
}
