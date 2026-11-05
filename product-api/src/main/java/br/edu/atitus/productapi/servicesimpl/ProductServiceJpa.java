package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private ProductResponse getResponse(ProductEntity entity, String environment, String targetCurrency){
        double convertedValue = entity.getPrice();
        if (! entity.getCurrency().equalsIgnoreCase(targetCurrency)) {
            try {
                var currency = currencyClientService.getCurrency(
                        entity.getCurrency(),
                        targetCurrency
                );
                convertedValue = entity.getPrice() * currency.conversionRate();
                environment += " - " + currency.environment();
            } catch (Exception e) {
                convertedValue = -1;
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
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable, String targetCurrency) throws Exception {
        var products = repository.findAllByActiveTrueAndSubcategory_ActiveTrueAndSubcategory_Category_ActiveTrue(pageable);
        String environment = "Product API running in port " + serverPort;

        return products.map(
                entity -> getResponse(entity, environment, targetCurrency)
        );
    }

    @Override
    @Transactional
    public ProductEntity save(ProductRequest request) throws Exception {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição inválida");
        }
        validateFields(
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
        product.setPrice(request.price());
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
        validateFields(
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
        product.setPrice(request.price());
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

    private void validateFields(
            String name,
            String brand,
            String model,
            String description,
            String currency,
            double price
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
        if (!Double.isFinite(price) || price <= 0) {
            throw badRequest("Preço inválido");
        }
        if (currency == null || currency.isBlank()) {
            throw badRequest("Moeda inválida");
        }
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