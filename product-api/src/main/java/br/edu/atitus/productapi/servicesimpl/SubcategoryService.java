package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.SubcategoryRequest;
import br.edu.atitus.productapi.dtos.SubcategoryResponse;
import br.edu.atitus.productapi.dtos.SubcategoryStatusRequest;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.repositories.CategoryRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubcategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final SlugGenerator slugGenerator;

    public SubcategoryService(
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository,
            SlugGenerator slugGenerator
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.slugGenerator = slugGenerator;
    }

    @Transactional
    public SubcategoryResponse create(SubcategoryRequest request) {
        var category = findActiveCategory(request == null ? null : request.categoryId());
        String name = validateName(request.name());
        String slug = validateAndGenerateSlug(name);
        ensureUnique(category.getId(), name, slug, null);

        var subcategory = new SubcategoryEntity();
        subcategory.setCategory(category);
        subcategory.setName(name);
        subcategory.setSlug(slug);
        subcategory.setActive(true);
        return save(subcategory);
    }

    @Transactional
    public SubcategoryResponse update(Long id, SubcategoryRequest request) {
        if (request == null) {
            throw badRequest("Requisição inválida");
        }
        var category = findActiveCategory(request.categoryId());
        String name = validateName(request.name());
        String slug = validateAndGenerateSlug(name);
        var subcategory = findSubcategory(id);
        ensureUnique(category.getId(), name, slug, id);

        subcategory.setCategory(category);
        subcategory.setName(name);
        subcategory.setSlug(slug);
        return save(subcategory);
    }

    @Transactional
    public SubcategoryResponse updateStatus(Long id, SubcategoryStatusRequest request) {
        if (request == null || request.active() == null) {
            throw badRequest("Status inválido");
        }
        var subcategory = findSubcategory(id);
        if (request.active() && !subcategory.getCategory().isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Não é possível ativar uma subcategoria de uma categoria inativa"
            );
        }
        subcategory.setActive(request.active());
        return save(subcategory);
    }

    private br.edu.atitus.productapi.entities.CategoryEntity findActiveCategory(Long categoryId) {
        if (categoryId == null) {
            throw badRequest("Categoria inválida");
        }
        return categoryRepository.findById(categoryId)
                .filter(br.edu.atitus.productapi.entities.CategoryEntity::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A categoria informada não existe ou está inativa"
                ));
    }

    private SubcategoryEntity findSubcategory(Long id) {
        return subcategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subcategoria não encontrada"));
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("Nome inválido");
        }
        String normalized = name.trim();
        if (normalized.length() > 180) {
            throw badRequest("O nome deve ter no máximo 180 caracteres");
        }
        return normalized;
    }

    private String validateAndGenerateSlug(String name) {
        String slug = slugGenerator.fromName(name);
        if (slug.isBlank() || slug.length() > 180) {
            throw badRequest("Não foi possível gerar um slug válido para o nome");
        }
        return slug;
    }

    private void ensureUnique(Long categoryId, String name, String slug, Long excludedId) {
        boolean duplicateName = excludedId == null
                ? subcategoryRepository.existsByCategory_IdAndNameIgnoreCase(categoryId, name)
                : subcategoryRepository.existsByCategory_IdAndNameIgnoreCaseAndIdNot(categoryId, name, excludedId);
        boolean duplicateSlug = excludedId == null
                ? subcategoryRepository.existsByCategory_IdAndSlug(categoryId, slug)
                : subcategoryRepository.existsByCategory_IdAndSlugAndIdNot(categoryId, slug, excludedId);
        if (duplicateName || duplicateSlug) {
            throw conflict("Já existe uma subcategoria com esse nome ou slug nesta categoria");
        }
    }

    private SubcategoryResponse save(SubcategoryEntity subcategory) {
        try {
            return SubcategoryResponse.fromEntity(subcategoryRepository.saveAndFlush(subcategory));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Já existe uma subcategoria com esse nome ou slug nesta categoria");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
