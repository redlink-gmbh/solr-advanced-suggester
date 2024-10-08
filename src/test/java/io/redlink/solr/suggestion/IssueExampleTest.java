package io.redlink.solr.suggestion;

import io.redlink.solr.suggestion.params.SuggestionRequestParams;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class IssueExampleTest extends AbstractTestBase {

    @BeforeClass
    public static void init() {
        assertU(adoc("_id_", "1",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_name", "Sebastian Vettel",
                "dynamic_multi_stored_suggest_analyzed_name", "Sebastien Loeb"));
        assertU(adoc("_id_", "2",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_name", "Sebastien Loeb"));
        assertU(adoc("_id_", "3",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_name", "My xa"));
        assertU(adoc("_id_", "4",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_name", "X-Alps"));
        assertU(adoc("_id_", "5",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_subtitle", "Subtitle 123"));
        assertU(adoc("_id_", "6",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_index", "12",
                "dynamic_multi_stored_suggest_analyzed_id", "ID123-456-789"));
        assertU(adoc("_id_", "7",
                "_type_", "Asset",
                "dynamic_single_stored_suggest_path_hierarchy1", "this/is a/test",
                "dynamic_single_stored_suggest_path_hierarchy2", "another/hierarchy"));
        assertU(adoc("_id_", "8",
                "_type_", "Asset",
                "dynamic_single_stored_suggest_path_hierarchy1", "this/is a/vettel",
                "dynamic_single_stored_suggest_path_hierarchy2", "vetter/test"));
        assertU(adoc("_id_", "9",
                "_type_", "Asset",
                "dynamic_multi_stored_suggest_analyzed_source", "The Real Dingo"));
        assertU(commit());
    }

    @Test
    public void testSpecialChars() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "loeb");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_name");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "dynamic_multi_stored_suggest_analyzed_name");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'loeb'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastien Loeb'][.='2']");

        params.set(CommonParams.Q, "sebasti");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'sebasti'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastien Loeb'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastian Vettel'][.='1']");

        params.set(CommonParams.Q, "Sebastien");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - simple facet suggestion for 'Sebastien'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastien Loeb'][.='2']");
    }

    /**
     * Attention! To enable this, make sure that you use the WhiteSpaceTokenizer (for query and index).
     */
    @Test
    @Ignore("At the moment synonyms are not supported in suggestions")
    public void testStaticSynonyms() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "xalps");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_name");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test synonym mapping for single facet", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='2']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='X-Alps'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='My xa'][.='1']");
    }

    /**
     * Load suggestions fails with searchterm "123"
     */
    @Test
    public void testWithNumbers() {
        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "123");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_subtitle");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_subtitle']/int[@name='Subtitle 123'][.='1']");

        params.set(CommonParams.Q, "456");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search without result", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='0']");

        params.set(CommonParams.Q, "Subtitel 123");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_subtitle']/int[@name='Subtitle 123'][.='1']");
    }

    /**
     * Test if parameters are parsed
     */
    @Test
    public void testParameterParsing() {

        ModifiableSolrParams params = new ModifiableSolrParams();
        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "sepastian");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_name");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        assertQ("suggester - spellcheck suggestion for 'sepastian'", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastian Vettel'][.='1']");
        //"//response/lst[@name='spellcheck']/lst[@name='collations']/str[@name='collation'][.='sebastian*']");//TODO api changed

        ModifiableSolrParams params2 = new ModifiableSolrParams();
        SolrQueryRequest req2 = new LocalSolrQueryRequest(core, params2);

        params2.add(SuggestionRequestParams.SUGGESTION, "true");
        params2.add(CommonParams.QT, "/suggester");
        params2.add(CommonParams.Q, "sepastian");
        params2.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_name");
        params2.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");
        params2.add("spellcheck.accuracy", "1");

        //TODO: This is a test issue, check why suggestion result is not appended
        assertQ("suggester - spellcheck suggestion for 'sepastian'", req2,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='0']");

    }

    @Test
    public void testEmptyNumberSuggestion() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "1");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_index");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search without result",
                req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']");

    }

    @Test
    public void testWithInvalidField() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "0");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_index");
        params.add(CommonParams.FQ, "notvalidfield:ASSET");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQEx("no error for 'notvalidfield'",
                "undefined field notvalidfield", req, SolrException.ErrorCode.BAD_REQUEST);

    }

    //Test should fail regarding the issue. TODO check schema.xml that is used as basis for the issue
    @Test
    public void testIdSearch() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "ID123");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_id");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search without result", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_id']/int[@name='ID123-456-789'][.='1']");
    }

    //Test: The full text suggestions (spellcheck) display values which do not deliver search results
    @Test
    public void testIdSearchNoSpellcheck() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "ID123-456-788");
        params.add(CommonParams.FQ, "dynamic_multi_stored_suggest_analyzed_id:ID123-456-788"); //filter for non existing facet -> no suggestion should be returned
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_id");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test number search without result", req,
                "not(//response/lst[@name='spellcheck'])");
    }

    //path field integration
    @Ignore("Path fields are not supported by Vind")
    public void testPathFieldIntegration() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "tes");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_single_stored_suggest_path_hierarchy1");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy1']/int[@name='this/is a/test'][.='1']");

        params.set(CommonParams.Q, "this");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='4']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy1']/int[@name='this/is a/test'][.='1']");

        params.set(CommonParams.Q, "vette");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_name");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_single_stored_suggest_path_hierarchy2");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='4']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy1']/int[@name='this/is a/vettel'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy2']/int[@name='vetter'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy2']/int[@name='vetter/test'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_name']/int[@name='Sebastian Vettel'][.='1']");

        params.set(CommonParams.Q, "this vet");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_single_stored_suggest_path_hierarchy1']/int[@name='this/is a/vettel'][.='1']");

        params.set(SuggestionRequestParams.SUGGESTION_STRATEGY, "exact");
        req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='0']");

    }

    @Test
    public void testTheRealDingo() {

        ModifiableSolrParams params = new ModifiableSolrParams();

        params.add(SuggestionRequestParams.SUGGESTION, "true");
        params.add(CommonParams.QT, "/suggester");
        params.add(CommonParams.Q, "The Real Dingo");
        params.add(SuggestionRequestParams.SUGGESTION_FIELD, "dynamic_multi_stored_suggest_analyzed_source");
        params.add(SuggestionRequestParams.SUGGESTION_DF, "suggestions");

        SolrQueryRequest req = new LocalSolrQueryRequest(core, params);

        assertQ("suggester - test path hierarchy", req,
                "//response/lst[@name='suggestions']/int[@name='suggestion_count'][.='1']",
                "//response/lst[@name='suggestions']/lst[@name='suggestion_facets']/lst[@name='dynamic_multi_stored_suggest_analyzed_source']/int[@name='The Real Dingo'][.='1']");

    }

}
