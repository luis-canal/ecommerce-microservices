package br.edu.atitus.authapi.repositories;

import br.edu.atitus.authapi.entities.RevokedTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedTokenEntity, String> {

    @Modifying
    @Query(value = "insert into tb_revoked_token (token_hash, expires_at) " +
            "values (:tokenHash, :expiresAt) on conflict (token_hash) do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("tokenHash") String tokenHash, @Param("expiresAt") Instant expiresAt);

    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, Instant expiresAt);
}
