package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.CategoryRequest;
import br.edu.atitus.productapi.dtos.CategoryResponse;
import br.edu.atitus.productapi.dtos.CategoryStatusRequest;
import br.edu.atitus.productapi.dtos.SubcategoryResponse;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.repositories.CategoryRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final SlugGenerator slugGenerator;

    public CategoryService(
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository,
            SlugGenerator slugGenerator
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.slugGenerator = slugGenerator;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubcategoryResponse> findActiveSubcategories(Long categoryId) {
        var category = categoryRepository.findById(categoryId)
                .filter(CategoryEntity::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));
        return subcategoryRepository.findAllByCategory_IdAndActiveTrueOrderByNameAsc(category.getId())
                .stream()
                .map(SubcategoryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = validateName(request == null ? null : request.name());
        String slug = slugGenerator.fromName(name);
        validateSlug(slug);
        ensureUnique(name, slug, null);

        var category = new CategoryEntity();
        category.setName(name);
        category.setSlug(slug);
        category.setActive(true);
        return save(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        String name = validateName(request == null ? null : request.name());
        String slug = slugGenerator.fromName(name);
        validateSlug(slug);

        var category = findCategory(id);
        ensureUnique(name, slug, id);
        category.setName(name);
        category.setSlug(slug);
        return save(category);
    }

    @Transactional
    public CategoryResponse updateStatus(Long id, CategoryStatusRequest request) {
        if (request == null || request.active() == null) {
            throw badRequest("Status inválido");
        }
        var category = findCategory(id);
        category.setActive(request.active());
        return save(category);
    }

    private CategoryEntity findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));
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

    private void validateSlug(String slug) {
        if (slug.isBlank() || slug.length() > 180) {
            throw badRequest("Não foi possível gerar um slug válido para o nome");
        }
    }

    private void ensureUnique(String name, String slug, Long excludedId) {
        boolean duplicateName = excludedId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        boolean duplicateSlug = excludedId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, excludedId);
        if (duplicateName || duplicateSlug) {
            throw conflict("Já existe uma categoria com esse nome ou slug");
        }
    }

    private CategoryResponse save(CategoryEntity category) {
        try {
            return CategoryResponse.fromEntity(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Já existe uma categoria com esse nome ou slug");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
