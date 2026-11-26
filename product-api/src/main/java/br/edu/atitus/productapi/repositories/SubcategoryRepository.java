package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.SubcategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<SubcategoryEntity, Long> {
    List<SubcategoryEntity> findAllByCategory_IdAndActiveTrueOrderByNameAsc(Long categoryId);

    @EntityGraph(attributePaths = "category")
    Page<SubcategoryEntity> findAllByOrderByNameAsc(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<SubcategoryEntity> findAllByCategory_IdOrderByNameAsc(Long categoryId, Pageable pageable);

    Optional<SubcategoryEntity> findByIdAndActiveTrueAndCategory_ActiveTrue(Long id);

    boolean existsByCategory_IdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategory_IdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);

    boolean existsByCategory_IdAndSlug(Long categoryId, String slug);

    boolean existsByCategory_IdAndSlugAndIdNot(Long categoryId, String slug, Long id);
}
