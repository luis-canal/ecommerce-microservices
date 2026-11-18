package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findByProductIdOrderByPositionAscIdAsc(Long productId);

    // Carrega as imagens de vários produtos em uma única consulta (product_id IN ...).
    List<ProductImageEntity> findByProductIdInOrderByProductIdAscPositionAscIdAsc(Collection<Long> productIds);

    Optional<ProductImageEntity> findByIdAndProductId(Long id, Long productId);

    Optional<ProductImageEntity> findFirstByProductIdAndPrimaryTrue(Long productId);

    long countByProductId(Long productId);

    @Query("select coalesce(max(image.position), 0) from ProductImageEntity image where image.productId = :productId")
    int findMaxPosition(@Param("productId") Long productId);
}
