package io.redlink.solr.suggestion;

import io.redlink.solr.suggestion.params.SuggestionRequestParams;
import java.time.Instant;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

public class PivotRegexBasedSuggestionTest extends AbstractTestBase {

    @BeforeClass
    public static void init() {
        assertU(adoc("_id_", "1",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_name", "sebastian vettel",
                "dynamic_multi_stored_suggest_string_name", "sebastien loeb",
                "dynamic_multi_stored_suggest_date_date", "2016-07-08T10:00:00Z"));
        assertU(adoc("_id_", "2",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_name", "sebastien loeb",
                "dynamic_multi_stored_suggest_date_date", "2016-09-07T10:00:00Z"));
        assertU(adoc("_id_", "3",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_name", "My xa",
                "dynamic_multi_stored_suggest_date_date", "2016-09-07T10:00:00Z"));
        assertU(adoc("_id_", "4",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_name", "X-Alps",
                "dynamic_multi_stored_suggest_date_date", "2016-09-08T10:00:00Z"));
        assertU(adoc("_id_", "5",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_subtitle", "Subtitle 123",
                "dynamic_multi_stored_suggest_date_date", "2016-09-08T10:00:00Z"));
        assertU(adoc("_id_", "6",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_index", "12",
                "dynamic_multi_stored_suggest_string_id", "MI123-456-789",
                "dynamic_multi_stored_suggest_date_date", "2016-09-08T10:00:00Z"));
        assertU(adoc("_id_", "7",
                "_type_", "Asset",
                "dynamic_single_stored_suggest_path_hierarchy1", "this/is a/test",
                "dynamic_single_stored_suggest_path_hierarchy2", "another/hierarchy",
                "dynamic_multi_stored_suggest_date_date", "2016-09-08T10:00:00Z"));
        assertU(adoc("_id_", "8",
                "_type_", "Asset",
                "dynamic_single_stored_suggest_path_hierarchy1", "this/is a/vettel",
                "dynamic_single_stored_suggest_path_hierarchy2", "vetter/test",
                "dynamic_multi_stored_suggest_date_date", "2016-09-08T10:00:00Z"));
        assertU(adoc("_id_", "9",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_string_name", "sebastien vettel",
                "dynamic_multi_stored_suggest_date_date", "2016-09-07T10:00:00Z"));
        assertU(commit());
    }

    /**
     *
     */
    @Test
    public void testSuggestionIntervals() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "lo");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_string_name");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL, "true");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL_FIELD, "dynamic_multi_stored_suggest_date_date");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL_LABEL, "last_week");
        params.add("suggestion.interval.range.last_week.start", "2016-09-07T00:00:0Z");
        params.add("suggestion.interval.range.last_week.end", "2016-09-14T00:00:0Z");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL_LABEL, "last_year");
        params.add("suggestion.interval.range.last_year.start", "2015-09-07T00:00:0Z");
        params.add("suggestion.interval.range.last_year.end", "2016-09-14T00:00:0Z");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'lo'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");

        params.set(CommonParams.Q, "seb");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'seb'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/int[@name='suggestion_count'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='3']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']");

        params.set(CommonParams.Q, "loeb");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'loeb'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_week']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");
    }

    @Test
    public void testSuggestionIntervalsDateMath() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "lo");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_string_name");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL, "true");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL_FIELD, "dynamic_multi_stored_suggest_date_date");
        params.add(SuggestionRequestParams.SUGGESTION_INTERVAL_LABEL, "last_year");
        params.add("suggestion.interval.range.last_year.start", "NOW/DAY-1YEAR");
        params.add("suggestion.interval.range.last_year.end", "NOW/DAY");
        params.add("NOW", Instant.parse("2016-09-14T00:00:00.000Z").toEpochMilli() + "");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'lo'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");

        params.set(CommonParams.Q, "seb");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'seb'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='3']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']");

        params.set(CommonParams.Q, "loeb");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'loeb'", req,
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_intervals']/lst[@name='last_year']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");
    }

    /**
     *
     */
    @Test
    public void testSuggestionNoIntervals() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "lo");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_string_name");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'lo'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");

        params.set(CommonParams.Q, "sebasti");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'sebasti'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='3']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastian vettel'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']");

        params.set(CommonParams.Q, "Sebastien");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'Sebastien'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']");
    }

    @Test
    public void testQueryPlusSeparator() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "se+lo");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_string_name");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'se lo'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']");

    }

    @Test
    public void testQueryTermLimit() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "se");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_string_name");
        params.add(SuggestionRequestParams.SUGGESTION_TERM_LIMIT, "1");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'sebasti'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='3']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastian vettel'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_string_name']/int[@name='sebastien vettel'][.='1']");
    }

}
