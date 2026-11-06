package br.edu.atitus.orderapi.clients;

import br.edu.atitus.orderapi.dtos.ProductResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

//@HttpExchange("/currencies")
@HttpExchange()
public interface ProductClient {

    @GetExchange("/products/{id}")
    ProductResponse getProductById(@PathVariable Long id, @RequestParam String targetCurrency);

}
