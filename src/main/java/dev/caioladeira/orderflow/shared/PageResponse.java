package dev.caioladeira.orderflow.shared;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, long totalPages) {
    public static <T> PageResponse<T> of(List<T> content, PageSpec page, long count) {
        return new PageResponse<>(List.copyOf(content), page.page(), page.size(), count,
                (count + page.size() - 1) / page.size());
    }
}
