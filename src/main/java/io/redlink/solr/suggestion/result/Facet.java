package io.redlink.solr.suggestion.result;

import java.util.Objects;

/**
 * Represents a simple facet POJO
 */
class Facet {
    private String name;
    private String value;
    private int count;

    public Facet(String name, String value, int count) {
        this.name = name;
        this.value = value;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
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
            return ((Facet) o).name.equals(this.name) && ((Facet) o).value.equals(this.value);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}