package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findByIdAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(Long id);

    Page<ProductEntity> findAllBySubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(Pageable pageable);
}