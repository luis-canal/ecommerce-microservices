package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, ProductSearchRepository {
    Optional<ProductEntity> findByIdAndActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(Long id);

    @Query("""
            select product from ProductEntity product
            join fetch product.subcategory subcategory
            join fetch subcategory.category
            where product.id = :id
            """)
    Optional<ProductEntity> findCartDataById(@Param("id") Long id);
}