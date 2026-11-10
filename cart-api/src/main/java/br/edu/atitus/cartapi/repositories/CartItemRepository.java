package br.edu.atitus.cartapi.repositories;

import br.edu.atitus.cartapi.entities.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    List<CartItemEntity> findAllByCartIdOrderByAddedAtAsc(Long cartId);

    Optional<CartItemEntity> findByCartIdAndProductId(Long cartId, Long productId);

    void deleteByCartIdAndProductId(Long cartId, Long productId);

    void deleteAllByCartId(Long cartId);
}
