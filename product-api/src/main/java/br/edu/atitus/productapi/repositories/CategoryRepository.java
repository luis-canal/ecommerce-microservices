package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
}
