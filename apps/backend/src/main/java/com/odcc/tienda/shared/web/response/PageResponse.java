package com.odcc.tienda.shared.web.response;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    public PageResponse {
        content = List.copyOf(content);
    }
}
