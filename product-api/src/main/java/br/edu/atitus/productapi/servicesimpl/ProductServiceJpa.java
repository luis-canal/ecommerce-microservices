package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.repositories.SubcategoryRepository;
import br.edu.atitus.productapi.services.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductServiceJpa implements ProductService {

    private final ProductRepository repository;
    private final CurrencyClientService currencyClientService;
    private final SubcategoryRepository subcategoryRepository;

    public ProductServiceJpa(
            ProductRepository repository,
            CurrencyClientService currencyClientService,
            SubcategoryRepository subcategoryRepository
    ) {
        this.repository = repository;
        this.currencyClientService = currencyClientService;
        this.subcategoryRepository = subcategoryRepository;
    }

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.promotion.message:Nenhuma Promoção Ativa}")
    private String promotionMessage;

    private ProductResponse getResponse(ProductEntity entity, String environment, String targetCurrency) {
        BigDecimal convertedValue = entity.getPrice();
        if (! entity.getCurrency().equalsIgnoreCase(targetCurrency)) {
            try {
                var currency = currencyClientService.getCurrency(
                        entity.getCurrency(),
                        targetCurrency
                );
                convertedValue = entity.getPrice()
                        .multiply(BigDecimal.valueOf(currency.conversionRate()))
                        .setScale(2, RoundingMode.HALF_UP);
                environment += " - " + currency.environment();
            } catch (Exception e) {
                convertedValue = BigDecimal.valueOf(-1);
                environment += " - ERROR: " + e.getMessage();
            }
        }
        return ProductResponse.fromEntity(
                entity,
                environment,
                promotionMessage,
                targetCurrency,
                convertedValue
        );
    }


    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id, String targetCurrency) throws Exception {
        var product = repository.findByIdAndActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        String environment = "Product API running in port " + serverPort;

        return getResponse(product, environment, targetCurrency);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ProductResponse> findAll(
            ProductSearchCriteria criteria,
            Pageable pageable,
            String targetCurrency
    ) {
        ProductSearchCriteria validatedCriteria = validateCriteria(criteria);
        validateSort(pageable.getSort());
        String responseCurrency = targetCurrency == null || targetCurrency.isBlank()
                ? "BRL"
                : targetCurrency.trim();
        Map<String, BigDecimal> conversionRatesToBrl = needsPriceConversion(validatedCriteria, pageable)
                ? getConversionRatesToBrl(repository.findCurrencies(validatedCriteria))
                : Map.of();
        var products = repository.search(validatedCriteria, pageable, conversionRatesToBrl);
        String environment = "Product API running in port " + serverPort;

        return products.map(
                entity -> getResponse(entity, environment, responseCurrency)
        );
    }

    @Override
    @Transactional
    public ProductEntity save(ProductRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição inválida");
        }
        BigDecimal price = validateFields(
                request.name(),
                request.brand(),
                request.model(),
                request.description(),
                request.currency(),
                request.price()
        );
        if (request.productType() == null) {
            throw badRequest("Tipo de produto obrigatório");
        }
        var subcategory = findActiveSubcategory(request.subcategoryId());

        var product = new ProductEntity();
        product.setName(request.name().trim());
        product.setBrand(request.brand().trim());
        product.setModel(request.model().trim());
        product.setDescription(request.description().trim());
        product.setPrice(price);
        product.setCurrency(request.currency().trim());
        product.setImage(request.image() != null ? request.image().trim() : null);
        product.setSubcategory(subcategory);
        product.setProductType(request.productType());
        product.setActive(true);
        return repository.save(product);
    }

    @Override
    @Transactional
    public ProductEntity update(Long id, ProductUpdateRequest request) {
        var product = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        if (request == null) {
            throw badRequest("Requisição inválida");
        }
        BigDecimal price = validateFields(
                request.name(),
                request.brand(),
                request.model(),
                request.description(),
                request.currency(),
                request.price()
        );
        var subcategory = findActiveSubcategory(request.subcategoryId());

        product.setName(request.name().trim());
        product.setBrand(request.brand().trim());
        product.setModel(request.model().trim());
        product.setDescription(request.description().trim());
        product.setPrice(price);
        product.setCurrency(request.currency().trim());
        product.setImage(request.image() != null ? request.image().trim() : null);
        product.setSubcategory(subcategory);
        return repository.save(product);
    }

    @Override
    @Transactional
    public ProductEntity updateStatus(Long id, ProductStatusRequest request) {
        if (request == null || request.active() == null) {
            throw badRequest("Status inválido");
        }
        var product = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        if (request.active()
                && (!product.getSubcategory().isActive() || !product.getSubcategory().getCategory().isActive())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Não é possível reativar um produto com categoria ou subcategoria inativa"
            );
        }
        product.setActive(request.active());
        return repository.save(product);
    }

    private BigDecimal validateFields(
            String name,
            String brand,
            String model,
            String description,
            String currency,
            BigDecimal price
    ) {
        if (name == null || name.isBlank()) {
            throw badRequest("Nome inválido");
        }
        if (brand == null || brand.isBlank()) {
            throw badRequest("Marca inválida");
        }
        if (model == null || model.isBlank()) {
            throw badRequest("Modelo inválido");
        }
        if (description == null || description.isBlank()) {
            throw badRequest("Descrição inválida");
        }
        if (price == null || price.signum() <= 0) {
            throw badRequest("Preço inválido");
        }
        BigDecimal normalizedPrice = price.setScale(2, RoundingMode.HALF_UP);
        if (normalizedPrice.signum() <= 0) {
            throw badRequest("Preço inválido");
        }
        if (normalizedPrice.precision() > 19) {
            throw badRequest("Preço excede o limite permitido");
        }
        if (currency == null || currency.isBlank()) {
            throw badRequest("Moeda inválida");
        }
        return normalizedPrice;
    }

    private ProductSearchCriteria validateCriteria(ProductSearchCriteria criteria) {
        if (criteria == null) {
            return new ProductSearchCriteria(null, null, null, null, null);
        }
        if (criteria.minPrice() != null && criteria.minPrice().signum() < 0) {
            throw badRequest("O preço mínimo não pode ser negativo");
        }
        if (criteria.maxPrice() != null && criteria.maxPrice().signum() < 0) {
            throw badRequest("O preço máximo não pode ser negativo");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw badRequest("O preço mínimo não pode ser superior ao preço máximo");
        }
        String normalizedName = criteria.name() == null || criteria.name().isBlank()
                ? null
                : criteria.name().trim();
        return new ProductSearchCriteria(
                normalizedName,
                criteria.categoryId(),
                criteria.subcategoryId(),
                criteria.minPrice(),
                criteria.maxPrice()
        );
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!order.getProperty().equals("name")
                    && !order.getProperty().equals("price")
                    && !order.getProperty().equals("description")) {
                throw badRequest("Ordenação não suportada: " + order.getProperty());
            }
        }
    }

    private boolean needsPriceConversion(ProductSearchCriteria criteria, Pageable pageable) {
        return criteria.minPrice() != null
                || criteria.maxPrice() != null
                || pageable.getSort().stream().anyMatch(order -> order.getProperty().equals("price"));
    }

    private Map<String, BigDecimal> getConversionRatesToBrl(Iterable<String> currencies) {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("BRL", BigDecimal.ONE);
        for (String currencyValue : currencies) {
            if (currencyValue == null || currencyValue.isBlank()) {
                throw conversionUnavailable();
            }
            String currency = currencyValue.toUpperCase(Locale.ROOT);
            if (currency.equals("BRL") || rates.containsKey(currency)) {
                continue;
            }
            try {
                var response = currencyClientService.getCurrency(currency, "BRL");
                if (response == null || response.conversionRate() == null
                        || response.conversionRate() <= 0 || !Double.isFinite(response.conversionRate())) {
                    throw conversionUnavailable();
                }
                rates.put(currency, BigDecimal.valueOf(response.conversionRate()));
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw conversionUnavailable();
            }
        }
        return Map.copyOf(rates);
    }

    private ResponseStatusException conversionUnavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível converter os preços para BRL"
        );
    }

    private SubcategoryEntity findActiveSubcategory(Long subcategoryId) {
        if (subcategoryId == null) {
            throw badRequest("Subcategoria obrigatória");
        }
        return subcategoryRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(subcategoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A subcategoria informada não existe ou está inativa"
                ));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}