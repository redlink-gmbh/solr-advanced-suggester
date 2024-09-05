package io.redlink.solr.suggestion.service;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.solr.core.SolrCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FieldAnalyzerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldAnalyzerService.class);

    private FieldAnalyzerService() {}

    /**
     * analyzes string like the given field
     *
     * @param field the name of the field
     * @param value the string to analyze
     * @return the analyzed string
     */
    public static String analyzeString(SolrCore core, String field, String value) {
        try {
            final StringBuilder b = new StringBuilder();
            try (TokenStream ts = core.getLatestSchema().getFieldType(field).getQueryAnalyzer().tokenStream(field, new StringReader(value))) {
                ts.reset();
                while (ts.incrementToken()) {
                    b.append(" ");
                    CharTermAttribute attr = ts.getAttribute(CharTermAttribute.class);
                    b.append(attr);
                }
            }

            return b.toString().trim();
        } catch (IOException e) {
            LOGGER.warn("Failed to analyze field '{}' with value '{}'", field, value, e);
            return value;
        }
    }

}
