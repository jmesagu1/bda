package com.databricks.apps.logs;

import java.io.Serializable;

/**
 * Created by jhon on 12/07/16.
 */
public class PieClass implements Serializable {
    private String label;
    private long value;

    public PieClass(String label, long value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public double getValue() {
        return value;
    }
}
