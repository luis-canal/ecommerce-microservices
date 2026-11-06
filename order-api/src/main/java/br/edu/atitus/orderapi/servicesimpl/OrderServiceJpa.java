package br.edu.atitus.orderapi.servicesimpl;

import br.edu.atitus.orderapi.clients.CurrencyClient;
import br.edu.atitus.orderapi.clients.ProductClient;
import br.edu.atitus.orderapi.dtos.CurrencyResponse;
import br.edu.atitus.orderapi.dtos.ProductResponse;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderItemEntity;
import br.edu.atitus.orderapi.repositories.OrderRepository;
import br.edu.atitus.orderapi.services.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceJpa implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final CurrencyClient currencyClient;

    public OrderServiceJpa(OrderRepository orderRepository, ProductClient productClient, CurrencyClient currencyClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.currencyClient = currencyClient;
    }

    @Override
    public OrderEntity createOrder(OrderEntity order, Long userId) {

        return orderRepository.save(order);
    }
    @Override
    public Page<OrderEntity> findOrdersByCustomerId(Long customerId, String targetCurrency, Pageable pageable) {
        Page<OrderEntity> orders = orderRepository.findByCustomerId(customerId, pageable);


        for (OrderEntity order : orders) {
            double totalPrice = 0.0;
            double totalConvertedPrice = 0.0;

            for (OrderItemEntity item : order.getItems()) {
                ProductResponse product = productClient.getProductById(item.getProductId(), targetCurrency);
                item.setProduct(product);
                totalPrice += item.getPriceAtPurchase() * item.getQuantity();

                CurrencyResponse currencyResponse = currencyClient.getCurrency(item.getCurrencyAtPurchase(), targetCurrency);
                item.setConvertedPriceAtPruchase(item.getPriceAtPurchase() * currencyResponse.conversionRate());
                totalConvertedPrice += item.getConvertedPriceAtPruchase() * item.getQuantity();
            }
            order.setTotalPrice(totalPrice);
            order.setTotalConvertedPrice(totalConvertedPrice);
        }
        return orders;
    }
}
