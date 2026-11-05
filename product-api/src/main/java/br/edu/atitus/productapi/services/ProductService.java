package br.edu.atitus.productapi.services;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse findById(Long id, String targetCurrency) throws Exception;
    Page<ProductResponse> findAll(ProductSearchCriteria criteria, Pageable pageable, String targetCurrency) throws Exception;
    ProductEntity save(ProductRequest request) throws Exception;
    ProductEntity update(Long id, ProductUpdateRequest request);
    ProductEntity updateStatus(Long id, ProductStatusRequest request);
}