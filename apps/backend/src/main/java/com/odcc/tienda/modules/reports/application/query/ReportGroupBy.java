package com.odcc.tienda.modules.reports.application.query;

import com.odcc.tienda.modules.reports.application.exception.InvalidReportFilterException;

import java.util.Locale;

public enum ReportGroupBy {
    DAY,
    WEEK,
    MONTH;

    public static ReportGroupBy from(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidReportFilterException("groupBy debe ser DAY, WEEK o MONTH");
        }
    }
}
