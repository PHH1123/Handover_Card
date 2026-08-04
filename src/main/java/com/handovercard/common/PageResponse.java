package com.handovercard.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        @Schema(description = "현재 페이지의 항목 목록")
        List<T> content,

        @Schema(description = "현재 페이지 번호 (0부터 시작)")
        int page,

        @Schema(description = "페이지 크기")
        int size,

        @Schema(description = "전체 항목 수")
        long totalElements,

        @Schema(description = "전체 페이지 수")
        int totalPages,

        @Schema(description = "다음 페이지 존재 여부")
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
