package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.enums.ProductStatusFilter;
import br.edu.atitus.productapi.enums.ProductType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.cloud.consul.enabled=false",
        "spring.config.import=optional:consul:",
        // A aplicação usa @EnableCaching; o slice JPA não sobe CacheManager.
        "spring.cache.type=none"
})
@ImportAutoConfiguration(CacheAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductSearchRepositoryImplTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Pageable BY_NAME = PageRequest.of(0, 50, Sort.by("name"));
    private static final Map<String, BigDecimal> RATES = Map.of("BRL", BigDecimal.ONE, "USD", new BigDecimal("5"));

    @Autowired
    private ProductRepository repository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private EntityManager entityManager;

    private CategoryEntity activeCategory;
    private CategoryEntity inactiveCategory;
    private SubcategoryEntity activeSubcategory;
    private SubcategoryEntity inactiveSubcategory;
    private SubcategoryEntity subcategoryOfInactiveCategory;
    private SubcategoryEntity inactiveSubcategoryOfInactiveCategory;

    @BeforeEach
    void seed() {
        // Remove os produtos de exemplo das migrations (imagens caem por ON DELETE CASCADE).
        entityManager.createQuery("delete from ProductEntity").executeUpdate();

        activeCategory = category("Categoria status ativa", true);
        inactiveCategory = category("Categoria status inativa", false);
        activeSubcategory = subcategory(activeCategory, "Sub ativa", true);
        inactiveSubcategory = subcategory(activeCategory, "Sub inativa", false);
        subcategoryOfInactiveCategory = subcategory(inactiveCategory, "Sub da categoria inativa", true);
        inactiveSubcategoryOfInactiveCategory = subcategory(
                inactiveCategory,
                "Sub inativa da categoria inativa",
                false
        );

        product("Cuia ativa barata", activeSubcategory, true, "BRL", "50.00");
        product("Cuia ativa cara", activeSubcategory, true, "BRL", "150.00");
        product("Cuia inativa", activeSubcategory, false, "BRL", "60.00");
        product("Bomba inativa", activeSubcategory, false, "BRL", "200.00");
        product("Cuia em categoria inativa", subcategoryOfInactiveCategory, true, "BRL", "70.00");
        product("Cuia em subcategoria inativa", inactiveSubcategory, true, "BRL", "80.00");
        product("Erva inativa em categoria inativa", subcategoryOfInactiveCategory, false, "BRL", "90.00");
        // 10 USD = 50 BRL
        product("Garrafa importada inativa", activeSubcategory, false, "USD", "10.00");
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void administrativeCategoryAndSubcategoryQueriesIncludeInactiveEntities() {
        var pageable = PageRequest.of(0, 100, Sort.by("name"));

        var categories = categoryRepository.findAllByOrderByNameAsc(pageable);
        assertTrue(categories.getContent().stream()
                .anyMatch(category -> category.getId().equals(activeCategory.getId())));
        assertTrue(categories.getContent().stream()
                .anyMatch(category -> category.getId().equals(inactiveCategory.getId())));

        var subcategories = subcategoryRepository.findAllByOrderByNameAsc(pageable);
        assertTrue(subcategories.getContent().stream()
                .anyMatch(subcategory -> subcategory.getId().equals(inactiveSubcategory.getId())));
        assertTrue(subcategories.getContent().stream()
                .anyMatch(subcategory -> subcategory.getId().equals(inactiveSubcategoryOfInactiveCategory.getId())));

        var childrenOfInactiveCategory = subcategoryRepository.findAllByCategory_IdOrderByNameAsc(
                inactiveCategory.getId(),
                pageable
        );
        assertEquals(2, childrenOfInactiveCategory.getTotalElements());
        assertTrue(childrenOfInactiveCategory.getContent().stream()
                .anyMatch(subcategory -> subcategory.getId().equals(subcategoryOfInactiveCategory.getId())));
        assertTrue(childrenOfInactiveCategory.getContent().stream()
                .anyMatch(subcategory -> subcategory.getId().equals(inactiveSubcategoryOfInactiveCategory.getId())));
    }

    @Test
    void activeKeepsTheCurrentPublicCatalogRule() {
        assertEquals(List.of("Cuia ativa barata", "Cuia ativa cara"), names(search(status(ProductStatusFilter.ACTIVE))));
    }

    @Test
    void criteriaWithoutStatusBehavesExactlyLikeActive() {
        var legacy = search(new ProductSearchCriteria(null, null, null, null, null));

        assertEquals(names(search(status(ProductStatusFilter.ACTIVE))), names(legacy));
        assertEquals(2, legacy.getTotalElements());
    }

    @Test
    void inactiveReturnsOnlyProductsWithActiveFalseRegardlessOfCategory() {
        assertEquals(
                List.of("Bomba inativa", "Cuia inativa", "Erva inativa em categoria inativa", "Garrafa importada inativa"),
                names(search(status(ProductStatusFilter.INACTIVE)))
        );
    }

    @Test
    void activeProductUnderInactiveCategoryOrSubcategoryAppearsOnlyInAll() {
        var onlyInAll = List.of("Cuia em categoria inativa", "Cuia em subcategoria inativa");

        var all = names(search(status(ProductStatusFilter.ALL)));
        var active = names(search(status(ProductStatusFilter.ACTIVE)));
        var inactive = names(search(status(ProductStatusFilter.INACTIVE)));

        assertEquals(8, all.size());
        assertEquals(true, all.containsAll(onlyInAll));
        assertEquals(false, active.stream().anyMatch(onlyInAll::contains));
        assertEquals(false, inactive.stream().anyMatch(onlyInAll::contains));
    }

    @Test
    void countQueryMatchesTheStatusFilterAcrossPages() {
        Page<ProductEntity> allFirst = repository.search(status(ProductStatusFilter.ALL), PageRequest.of(0, 3, Sort.by("name")), RATES);
        assertEquals(8, allFirst.getTotalElements());
        assertEquals(3, allFirst.getTotalPages());
        assertEquals(3, allFirst.getContent().size());

        Page<ProductEntity> inactiveSecond = repository.search(status(ProductStatusFilter.INACTIVE), PageRequest.of(1, 3, Sort.by("name")), RATES);
        assertEquals(4, inactiveSecond.getTotalElements());
        assertEquals(2, inactiveSecond.getTotalPages());
        assertEquals(List.of("Garrafa importada inativa"), names(inactiveSecond));

        Page<ProductEntity> active = repository.search(status(ProductStatusFilter.ACTIVE), PageRequest.of(0, 1, Sort.by("name")), RATES);
        assertEquals(2, active.getTotalElements());
        assertEquals(List.of("Cuia ativa barata"), names(active));
    }

    @Test
    void statusCombinesWithNameFilter() {
        assertEquals(List.of("Cuia inativa"),
                names(search(new ProductSearchCriteria("cuia", null, null, null, null, ProductStatusFilter.INACTIVE))));
        assertEquals(5,
                search(new ProductSearchCriteria("CUIA", null, null, null, null, ProductStatusFilter.ALL)).getTotalElements());
    }

    @Test
    void statusCombinesWithCategoryAndSubcategoryFilters() {
        assertEquals(
                List.of("Bomba inativa", "Cuia ativa barata", "Cuia ativa cara", "Cuia em subcategoria inativa",
                        "Cuia inativa", "Garrafa importada inativa"),
                names(search(new ProductSearchCriteria(null, activeCategory.getId(), null, null, null, ProductStatusFilter.ALL)))
        );
        assertEquals(
                List.of("Erva inativa em categoria inativa"),
                names(search(new ProductSearchCriteria(null, null, subcategoryOfInactiveCategory.getId(), null, null,
                        ProductStatusFilter.INACTIVE)))
        );
        assertEquals(
                List.of("Cuia ativa barata", "Cuia ativa cara"),
                names(search(new ProductSearchCriteria(null, null, activeSubcategory.getId(), null, null,
                        ProductStatusFilter.ACTIVE)))
        );
    }

    @Test
    void statusCombinesWithConvertedPriceFilterAndSort() {
        var page = repository.search(
                new ProductSearchCriteria(null, null, null, null, new BigDecimal("100.00"), ProductStatusFilter.INACTIVE),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price")),
                RATES
        );

        // Garrafa: 10 USD * 5 = 50 BRL.
        assertEquals(List.of("Garrafa importada inativa", "Cuia inativa", "Erva inativa em categoria inativa"), names(page));
        assertEquals(3, page.getTotalElements());

        assertEquals(
                List.of("Cuia inativa", "Cuia em categoria inativa", "Cuia em subcategoria inativa"),
                names(repository.search(
                        new ProductSearchCriteria(null, null, null, new BigDecimal("60.00"), new BigDecimal("80.00"),
                                ProductStatusFilter.ALL),
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price")),
                        RATES
                ))
        );
    }

    @Test
    void currencyLookupRespectsStatus() {
        assertEquals(List.of("BRL"), repository.findCurrencies(status(ProductStatusFilter.ACTIVE)));
        assertEquals(List.of("BRL", "USD"),
                repository.findCurrencies(status(ProductStatusFilter.INACTIVE)).stream().sorted().toList());
    }

    private Page<ProductEntity> search(ProductSearchCriteria criteria) {
        return repository.search(criteria, BY_NAME, RATES);
    }

    private static ProductSearchCriteria status(ProductStatusFilter status) {
        return new ProductSearchCriteria(null, null, null, null, null, status);
    }

    private static List<String> names(Page<ProductEntity> page) {
        return page.getContent().stream().map(ProductEntity::getName).toList();
    }

    private CategoryEntity category(String name, boolean active) {
        var category = new CategoryEntity();
        category.setName(name);
        category.setSlug(name.toLowerCase().replace(' ', '-'));
        category.setActive(active);
        entityManager.persist(category);
        return category;
    }

    private SubcategoryEntity subcategory(CategoryEntity category, String name, boolean active) {
        var subcategory = new SubcategoryEntity();
        subcategory.setCategory(category);
        subcategory.setName(name);
        subcategory.setSlug(name.toLowerCase().replace(' ', '-'));
        subcategory.setActive(active);
        entityManager.persist(subcategory);
        return subcategory;
    }

    private void product(String name, SubcategoryEntity subcategory, boolean active, String currency, String price) {
        var product = new ProductEntity();
        product.setName(name);
        product.setBrand("Marca");
        product.setModel("Modelo");
        product.setDescription(name);
        product.setCurrency(currency);
        product.setPrice(new BigDecimal(price));
        product.setProductType(ProductType.SINGLE_UNIT);
        product.setActive(active);
        product.setSubcategory(subcategory);
        entityManager.persist(product);
    }
}
