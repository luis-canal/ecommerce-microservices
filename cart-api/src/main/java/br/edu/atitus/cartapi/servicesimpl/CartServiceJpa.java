package br.edu.atitus.cartapi.servicesimpl;

import br.edu.atitus.cartapi.clients.InventoryApiClient;
import br.edu.atitus.cartapi.clients.ProductApiClient;
import br.edu.atitus.cartapi.dtos.CartItemRequest;
import br.edu.atitus.cartapi.dtos.CartItemResponse;
import br.edu.atitus.cartapi.dtos.CartResponse;
import br.edu.atitus.cartapi.dtos.InventoryData;
import br.edu.atitus.cartapi.dtos.ProductData;
import br.edu.atitus.cartapi.entities.CartEntity;
import br.edu.atitus.cartapi.entities.CartItemEntity;
import br.edu.atitus.cartapi.repositories.CartItemRepository;
import br.edu.atitus.cartapi.repositories.CartRepository;
import br.edu.atitus.cartapi.services.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CartServiceJpa implements CartService {

    private static final String SINGLE_UNIT = "SINGLE_UNIT";
    private static final String MULTI_UNIT = "MULTI_UNIT";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductApiClient productApiClient;
    private final InventoryApiClient inventoryApiClient;

    public CartServiceJpa(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductApiClient productApiClient,
            InventoryApiClient inventoryApiClient
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productApiClient = productApiClient;
        this.inventoryApiClient = inventoryApiClient;
    }

    @Override
    @Transactional
    public CartResponse getCart(Long customerId) {
        CartEntity cart = ensureCart(customerId);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(Long customerId, CartItemRequest request) {
        if (request == null || request.productId() == null || request.productId() <= 0) {
            throw badRequest("productId deve ser positivo.");
        }
        validateQuantity(request.quantity(), null);
        CartEntity cart = lockCart(customerId);
        ProductData product = requireActiveProduct(request.productId());
        InventoryData inventory = getInventory(product);
        validateQuantity(request.quantity(), product.productType());
        validateAvailability(request.quantity(), inventory);

        Optional<CartItemEntity> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.id());
        if (existing.isPresent()) {
            existing.get().updateQuantity(request.quantity());
        } else {
            cartItemRepository.save(new CartItemEntity(cart, product.id(), request.quantity()));
        }
        cart.touch();
        cartRepository.saveAndFlush(cart);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(Long customerId, Long productId, Integer quantity) {
        validateProductId(productId);
        CartEntity cart = lockCart(customerId);
        CartItemEntity item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado no carrinho."));
        ProductData product = requireActiveProduct(productId);
        InventoryData inventory = getInventory(product);
        validateQuantity(quantity, product.productType());
        validateAvailability(quantity, inventory);

        item.updateQuantity(quantity);
        cart.touch();
        cartRepository.saveAndFlush(cart);
        return buildCartResponse(cart);
    }

    @Override
    @Transactional
    public void removeItem(Long customerId, Long productId) {
        validateProductId(productId);
        CartEntity cart = lockCart(customerId);
        if (cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado no carrinho.");
        }
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        cart.touch();
        cartRepository.saveAndFlush(cart);
    }

    @Override
    @Transactional
    public void clearCart(Long customerId) {
        CartEntity cart = lockCart(customerId);
        if (!cartItemRepository.findAllByCartIdOrderByAddedAtAsc(cart.getId()).isEmpty()) {
            cartItemRepository.deleteAllByCartId(cart.getId());
            cart.touch();
            cartRepository.saveAndFlush(cart);
        }
    }

    private CartEntity ensureCart(Long customerId) {
        cartRepository.createIfMissing(customerId);
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("Não foi possível criar o carrinho."));
    }

    private CartEntity lockCart(Long customerId) {
        ensureCart(customerId);
        return cartRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalStateException("Não foi possível localizar o carrinho."));
    }

    private CartResponse buildCartResponse(CartEntity cart) {
        List<CartItemEntity> items = cartItemRepository.findAllByCartIdOrderByAddedAtAsc(cart.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean estimateComplete = true;
        List<CartItemResponse> responses = new java.util.ArrayList<>(items.size());

        for (CartItemEntity item : items) {
            Optional<ProductData> productResult = productApiClient.getProduct(item.getProductId());
            if (productResult.isEmpty()) {
                estimateComplete = false;
                responses.add(unavailableProduct(item));
                continue;
            }

            ProductData product = productResult.get();
            BigDecimal unitPrice = requireValidPrice(product);
            InventoryData inventory = inventoryApiClient.getAvailability(product.id()).orElse(null);
            validateInventoryProductType(product, inventory);
            long quantityOnHand = inventory == null ? 0 : inventory.quantityOnHand();
            long quantityReserved = inventory == null ? 0 : inventory.quantityReserved();
            long availableQuantity = inventory == null ? 0 : inventory.availableQuantity();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.getDesiredQuantity()));
            subtotal = subtotal.add(itemSubtotal);
            responses.add(new CartItemResponse(
                    item.getProductId(),
                    product.name(),
                    product.brand(),
                    product.model(),
                    product.image(),
                    product.productType(),
                    Boolean.TRUE.equals(product.active()),
                    true,
                    item.getDesiredQuantity(),
                    quantityOnHand,
                    quantityReserved,
                    availableQuantity,
                    unitPrice,
                    itemSubtotal,
                    Boolean.TRUE.equals(product.active()) && item.getDesiredQuantity() <= availableQuantity,
                    item.getAddedAt(),
                    item.getUpdatedAt()
            ));
        }

        return new CartResponse(
                cart.getId(),
                cart.getVersion(),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                "BRL",
                estimateComplete ? subtotal : null,
                estimateComplete,
                List.copyOf(responses)
        );
    }

    private CartItemResponse unavailableProduct(CartItemEntity item) {
        return new CartItemResponse(
                item.getProductId(),
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                item.getDesiredQuantity(),
                0,
                0,
                0,
                null,
                null,
                false,
                item.getAddedAt(),
                item.getUpdatedAt()
        );
    }

    private ProductData requireActiveProduct(Long productId) {
        ProductData product = productApiClient.getProduct(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado ou inativo."));
        if (!Boolean.TRUE.equals(product.active())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto inativo não pode ser adicionado ao carrinho.");
        }
        requireValidPrice(product);
        if (!SINGLE_UNIT.equals(product.productType()) && !MULTI_UNIT.equals(product.productType())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O product-api retornou um tipo de produto inválido.");
        }
        return product;
    }

    private InventoryData getInventory(ProductData product) {
        InventoryData inventory = inventoryApiClient.getAvailability(product.id()).orElse(null);
        validateInventoryProductType(product, inventory);
        return inventory;
    }

    private void validateInventoryProductType(ProductData product, InventoryData inventory) {
        if (inventory != null && !product.productType().equals(inventory.productType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "O tipo de produto diverge entre product-api e inventory-api."
            );
        }
    }

    private BigDecimal requireValidPrice(ProductData product) {
        if (product.convertedPrice() == null || product.convertedPrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O product-api retornou um preço BRL inválido.");
        }
        return product.convertedPrice();
    }

    private void validateQuantity(Integer quantity, String productType) {
        if (quantity == null || quantity <= 0) {
            throw badRequest("A quantidade deve ser um inteiro positivo.");
        }
        if (SINGLE_UNIT.equals(productType) && quantity != 1) {
            throw badRequest("Produtos SINGLE_UNIT aceitam somente quantidade 1.");
        }
        if (productType != null && !SINGLE_UNIT.equals(productType) && !MULTI_UNIT.equals(productType)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O product-api retornou um tipo de produto inválido.");
        }
    }

    private void validateAvailability(Integer quantity, InventoryData inventory) {
        long available = inventory == null ? 0 : inventory.availableQuantity();
        if (quantity > available) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A quantidade desejada excede a disponibilidade atual do estoque."
            );
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw badRequest("productId deve ser positivo.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
