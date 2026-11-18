package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // Bloqueia a linha do produto para serializar alterações nas suas imagens.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from ProductEntity product where product.id = :id")
    Optional<ProductEntity> lockById(@Param("id") Long id);
}