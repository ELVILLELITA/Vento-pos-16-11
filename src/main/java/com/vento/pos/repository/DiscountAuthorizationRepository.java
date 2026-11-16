package com.vento.pos.repository;

import com.vento.pos.entity.DiscountAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountAuthorizationRepository extends JpaRepository<DiscountAuthorization, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DiscountAuthorization a where a.tokenHash = :tokenHash")
    Optional<DiscountAuthorization> findForUpdate(@Param("tokenHash") String tokenHash);
}
