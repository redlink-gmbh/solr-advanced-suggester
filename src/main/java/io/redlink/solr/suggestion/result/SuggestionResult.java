package io.redlink.solr.suggestion.result;

/**
 * ...
 * <p/>
 * Author: Thomas Kurz (tkurz@apache.org)
 */
public interface SuggestionResult {

    Object write();
    int getCount();

}
