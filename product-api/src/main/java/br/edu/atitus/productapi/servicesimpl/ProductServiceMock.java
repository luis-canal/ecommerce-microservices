package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductSearchCriteria;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.enums.ProductType;
import br.edu.atitus.productapi.services.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ProductServiceMock implements ProductService {

    private final List<ProductEntity> database = new ArrayList<>();
    private final CurrencyClientService currencyClientService;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.promotion.message:Nenhuma Promoção Ativa}")
    private String promotionMessage;

    public ProductServiceMock(CurrencyClientService currencyClientService) {
        this.currencyClientService = currencyClientService;
        this.database.add(mockProduct(1L, "iPhone 15 128GB", "Apple", "iPhone 15", "799.00"));
        this.database.add(mockProduct(2L, "iPhone 15 Pro 256GB", "Apple", "iPhone 15 Pro", "1099.00"));
        this.database.add(mockProduct(3L, "Galaxy S24 256GB", "Samsung", "Galaxy S24", "859.00"));
        this.database.add(mockProduct(4L, "Galaxy S24 Ultra 512GB", "Samsung", "Galaxy S24 Ultra", "1299.00"));
    }

    private ProductEntity mockProduct(Long id, String name, String brand, String model, String price) {
        var product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setBrand(brand);
        product.setModel(model);
        product.setDescription("Smartphone " + brand + " " + model);
        product.setCurrency("USD");
        product.setPrice(new BigDecimal(price));
        product.setImage("http://image.com/image.jpg");
        product.setProductType(ProductType.MULTI_UNIT);
        return product;
    }

    @Override
    public ProductEntity save(ProductRequest dto) {
        throw new UnsupportedOperationException("Cadastro não disponível no serviço mock");
    }

    @Override
    public ProductEntity update(Long id, ProductUpdateRequest request) {
        throw new UnsupportedOperationException("Edição não disponível no serviço mock");
    }

    @Override
    public ProductEntity updateStatus(Long id, ProductStatusRequest request) {
        throw new UnsupportedOperationException("Alteração de status não disponível no serviço mock");
    }

    @Override
    public ProductResponse findById(Long id, String targetCurrency) {
        ProductEntity product = database.stream()
                .filter(item -> item.getId().equals(id))
                .filter(ProductEntity::isActive)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        String responseCurrency = targetCurrency == null || targetCurrency.isBlank() ? "BRL" : targetCurrency;
        return ProductResponse.fromEntity(
                product,
                "Product API running in port " + serverPort,
                promotionMessage,
                responseCurrency,
                convert(product, responseCurrency)
        );
    }

    @Override
    public Page<ProductResponse> findAll(
            ProductSearchCriteria criteria,
            Pageable pageable,
            String targetCurrency
    ) {
        ProductSearchCriteria filters = criteria == null
                ? new ProductSearchCriteria(null, null, null, null, null)
                : criteria;
        if (filters.minPrice() != null && filters.minPrice().signum() < 0
                || filters.maxPrice() != null && filters.maxPrice().signum() < 0
                || filters.minPrice() != null && filters.maxPrice() != null
                && filters.minPrice().compareTo(filters.maxPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faixa de preço inválida");
        }

        List<ProductEntity> filtered = database.stream()
                .filter(ProductEntity::isActive)
                .filter(product -> filters.name() == null || filters.name().isBlank()
                        || product.getName().toLowerCase(Locale.ROOT)
                        .contains(filters.name().trim().toLowerCase(Locale.ROOT)))
                .filter(product -> filters.categoryId() == null && filters.subcategoryId() == null)
                .filter(product -> filters.minPrice() == null
                        || convert(product, "BRL").compareTo(filters.minPrice()) >= 0)
                .filter(product -> filters.maxPrice() == null
                        || convert(product, "BRL").compareTo(filters.maxPrice()) <= 0)
                .toList();

        Comparator<ProductEntity> comparator = comparator(pageable.getSort());
        if (comparator != null) {
            filtered = new ArrayList<>(filtered);
            filtered.sort(comparator);
        }

        String responseCurrency = targetCurrency == null || targetCurrency.isBlank() ? "BRL" : targetCurrency;
        List<ProductResponse> responses = filtered.stream()
                .map(product -> ProductResponse.fromEntity(
                        product,
                        "Product API running in port " + serverPort,
                        promotionMessage,
                        responseCurrency,
                        convert(product, responseCurrency)
                ))
                .toList();
        int fromIndex = Math.toIntExact(pageable.getOffset());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), responses.size());
        List<ProductResponse> pageContent = fromIndex >= responses.size()
                ? List.of()
                : responses.subList(fromIndex, toIndex);
        return new PageImpl<>(pageContent, pageable, responses.size());
    }

    private Comparator<ProductEntity> comparator(Sort sort) {
        Comparator<ProductEntity> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<ProductEntity> next = switch (order.getProperty()) {
                case "name" -> Comparator.comparing(product -> product.getName().toLowerCase(Locale.ROOT));
                case "price" -> Comparator.comparing(product -> convert(product, "BRL"));
                case "description" -> Comparator.comparing(ProductEntity::getDescription);
                default -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Ordenação não suportada: " + order.getProperty()
                );
            };
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator;
    }

    private BigDecimal convert(ProductEntity product, String targetCurrency) {
        if (product.getCurrency().equalsIgnoreCase(targetCurrency)) {
            return product.getPrice();
        }
        try {
            var response = currencyClientService.getCurrency(product.getCurrency(), targetCurrency);
            return product.getPrice()
                    .multiply(BigDecimal.valueOf(response.conversionRate()))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível converter o preço para " + targetCurrency
            );
        }
    }
}
