package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.SubcategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcategoryRepository extends JpaRepository<SubcategoryEntity, Long> {
}
