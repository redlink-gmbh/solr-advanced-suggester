package io.redlink.solr.suggestion.result;

import io.redlink.solr.suggestion.SuggestionRequestHandler;
import java.time.LocalDateTime;
import java.util.Objects;

class Interval {

    private int count;
    private LocalDateTime start;
    private LocalDateTime end;
    private SuggestionResult facets;

    public Interval(LocalDateTime start, LocalDateTime end, int limit, SuggestionRequestHandler.LimitType limitType) {
        this.start = start;
        this.end = end;
        this.facets = new SuggesionResultSingle(limit, limitType);

    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public SuggestionResult getFacets() {
        return facets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        try {
            return ((Interval) o).start.isEqual(this.start) && ((Interval) o).end.isEqual(this.end);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}