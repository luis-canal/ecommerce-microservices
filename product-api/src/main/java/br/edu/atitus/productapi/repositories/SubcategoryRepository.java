package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.SubcategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<SubcategoryEntity, Long> {
    List<SubcategoryEntity> findAllByCategory_IdAndActiveTrueOrderByNameAsc(Long categoryId);

    Optional<SubcategoryEntity> findByIdAndActiveTrueAndCategory_ActiveTrue(Long id);

    boolean existsByCategory_IdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategory_IdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);

    boolean existsByCategory_IdAndSlug(Long categoryId, String slug);

    boolean existsByCategory_IdAndSlugAndIdNot(Long categoryId, String slug, Long id);
}
