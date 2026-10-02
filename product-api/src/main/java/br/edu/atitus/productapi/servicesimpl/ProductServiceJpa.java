package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.clients.CurrencyClient;
import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.services.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Service
public class ProductServiceJpa implements ProductService {

    private final ProductRepository repository;
    private final CurrencyClient currencyClient;

    public ProductServiceJpa(ProductRepository repository, CurrencyClient currencyClient) {
        this.repository = repository;
        this.currencyClient = currencyClient;
    }

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.promotion.message:Nenhuma Promoção Ativa}")
    private String promotionMessage;

    private Double getConversionRate(String source, String target) throws Exception {
        // Buscar da currency-api a taxa de conversão entre source e target
        if (source.equalsIgnoreCase(target)) {
            return 1.0;
        }
        return null;
    }


    @Override
    public ProductResponse findById(Long id, String targetCurrency) throws Exception {
        var product = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        String environment = "Product API running in port " + serverPort;

        Double convertedValue = product.getPrice();
        if (! product.getCurrency().equalsIgnoreCase(targetCurrency)) {
            var currency = currencyClient.getCurrency(product.getCurrency(), targetCurrency);
            convertedValue = product.getPrice() * currency.conversionRate();
            environment += " - " + currency.environment();
        }

        return ProductResponse.fromEntity(
                product,
                environment,
                promotionMessage,
                targetCurrency,
                convertedValue
        );
    }

    @Override
    public Page<ProductResponse> findAll(Pageable pageable, String targetCurrency) throws Exception {
        var products = repository.findAll(pageable);
        String environment = "Product API running in port " + serverPort;

        return products.map(
                entity -> ProductResponse.fromEntity(
                        entity,
                        environment,
                        promotionMessage,
                        targetCurrency,
                        0
                )
        );
    }

    @Override
    public ProductEntity save(ProductRequest request) throws Exception {
        return null;
    }
}