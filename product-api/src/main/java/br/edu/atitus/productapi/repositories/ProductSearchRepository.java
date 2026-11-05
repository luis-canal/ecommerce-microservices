package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.entities.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductSearchRepository {
    List<String> findCurrencies(ProductSearchCriteria criteria);

    Page<ProductEntity> search(
            ProductSearchCriteria criteria,
            Pageable pageable,
            Map<String, BigDecimal> conversionRatesToBrl
    );
}
