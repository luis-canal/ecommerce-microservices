package br.edu.atitus.analyticsapi.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Página retornada pelos serviços de origem. Aceita tanto o formato serializado via DTO
 * ({"content": [], "page": {...}}) quanto o formato direto do PageImpl.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(
        List<T> content,
        PageMetadata page,
        Integer totalPages,
        Integer number,
        Boolean last
) {

    public static <T> PageResponse<T> of(List<T> content, int number, int totalPages) {
        return new PageResponse<>(content, new PageMetadata(content.size(), number, (long) content.size(), totalPages),
                null, null, null);
    }

    public List<T> safeContent() {
        return content == null ? List.of() : content;
    }

    public boolean hasNext(int requestedPage, int pageSize) {
        if (last != null) {
            return !last;
        }
        Integer pages = page != null ? page.totalPages() : totalPages;
        if (pages != null) {
            return requestedPage + 1 < pages;
        }
        return safeContent().size() >= pageSize;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageMetadata(Integer size, Integer number, Long totalElements, Integer totalPages) {
    }
}
