package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.entities.ProductEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<String> findCurrencies(ProductSearchCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<ProductEntity> product = query.from(ProductEntity.class);
        ProductSearchCriteria nonPriceFilters = new ProductSearchCriteria(
                criteria.name(),
                criteria.categoryId(),
                criteria.subcategoryId(),
                null,
                null
        );
        query.select(builder.upper(product.get("currency")))
                .distinct(true)
                .where(predicates(builder, product, nonPriceFilters, null).toArray(Predicate[]::new));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public Page<ProductEntity> search(
            ProductSearchCriteria criteria,
            Pageable pageable,
            Map<String, BigDecimal> conversionRatesToBrl
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        boolean hasPriceBounds = criteria.minPrice() != null || criteria.maxPrice() != null;

        CriteriaQuery<ProductEntity> query = builder.createQuery(ProductEntity.class);
        Root<ProductEntity> product = query.from(ProductEntity.class);
        Expression<BigDecimal> convertedPrice = hasPriceBounds || sortsByPrice(pageable)
                ? convertedPrice(builder, product, conversionRatesToBrl)
                : null;
        query.select(product)
                .where(predicates(builder, product, criteria, convertedPrice).toArray(Predicate[]::new));
        if (pageable.getSort().isSorted()) {
            query.orderBy(orders(builder, product, pageable.getSort(), convertedPrice));
        }

        TypedQuery<ProductEntity> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<ProductEntity> countProduct = countQuery.from(ProductEntity.class);
        Expression<BigDecimal> countConvertedPrice = hasPriceBounds
                ? convertedPrice(builder, countProduct, conversionRatesToBrl)
                : null;
        countQuery.select(builder.count(countProduct))
                .where(predicates(builder, countProduct, criteria, countConvertedPrice).toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(
            CriteriaBuilder builder,
            Root<ProductEntity> product,
            ProductSearchCriteria criteria,
            Expression<BigDecimal> convertedPrice
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.isTrue(product.get("active")));
        predicates.add(builder.isTrue(product.get("subcategory").get("active")));
        predicates.add(builder.isTrue(product.get("subcategory").get("category").get("active")));

        if (criteria.name() != null && !criteria.name().isBlank()) {
            String pattern = "%" + escapeLike(criteria.name().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(builder.like(builder.lower(product.get("name")), pattern, '\\'));
        }
        if (criteria.categoryId() != null) {
            predicates.add(builder.equal(
                    product.get("subcategory").get("category").get("id"),
                    criteria.categoryId()
            ));
        }
        if (criteria.subcategoryId() != null) {
            predicates.add(builder.equal(product.get("subcategory").get("id"), criteria.subcategoryId()));
        }
        if (criteria.minPrice() != null) {
            predicates.add(builder.greaterThanOrEqualTo(convertedPrice, criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            predicates.add(builder.lessThanOrEqualTo(convertedPrice, criteria.maxPrice()));
        }
        return predicates;
    }

    private List<jakarta.persistence.criteria.Order> orders(
            CriteriaBuilder builder,
            Root<ProductEntity> product,
            Sort sort,
            Expression<BigDecimal> convertedPrice
    ) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Expression<?> expression = switch (order.getProperty()) {
                case "name" -> builder.lower(product.get("name"));
                case "price" -> convertedPrice;
                case "description" -> product.get("description");
                default -> throw new IllegalArgumentException("Campo de ordenação não suportado");
            };
            orders.add(order.isAscending() ? builder.asc(expression) : builder.desc(expression));
        }
        return orders;
    }

    private Expression<BigDecimal> convertedPrice(
            CriteriaBuilder builder,
            Root<ProductEntity> product,
            Map<String, BigDecimal> conversionRatesToBrl
    ) {
        CriteriaBuilder.Case<BigDecimal> conversionRate = builder.selectCase();
        conversionRatesToBrl.forEach((currency, rate) ->
                conversionRate.when(builder.equal(builder.upper(product.get("currency")), currency), rate)
        );
        Expression<BigDecimal> unroundedPrice = builder.prod(
                product.<BigDecimal>get("price"),
                conversionRate.otherwise(builder.nullLiteral(BigDecimal.class))
        );
        return builder.function("round", BigDecimal.class, unroundedPrice, builder.literal(2));
    }

    private boolean sortsByPrice(Pageable pageable) {
        return pageable.getSort().stream().anyMatch(order -> order.getProperty().equals("price"));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
