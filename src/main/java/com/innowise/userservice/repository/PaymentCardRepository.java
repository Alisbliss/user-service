package com.innowise.userservice.repository;

import com.innowise.userservice.entity.PaymentCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, Long>, JpaSpecificationExecutor<PaymentCard> {
    List<PaymentCard> findByUserId(long userId);
    long countByUserId (long userId);
    boolean existsByNumber(String number);

    @Query("SELECT c FROM PaymentCard c WHERE c.user.id = :userId AND c.active = true")
    List<PaymentCard> findActiveByUserId(@Param("userId") Long userId);
}
