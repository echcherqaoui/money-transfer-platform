package com.moneytransfer.transaction.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(List<T> content,
                                   long totalCount,
                                   int page,
                                   int size,
                                   boolean hasMore,
                                   int totalPages) {
    public static <T> PaginatedResponse<T> fromPage(Page<T> page) {
        return new PaginatedResponse<>(
              page.getContent(),
              page.getTotalElements(),
              page.getNumber(),
              page.getSize(),
              page.hasNext(),
              page.getTotalPages()
        );
    }
}