package com.innowise.userservice.service;

import com.innowise.userservice.dto.PaymentCardCreateRequest;
import com.innowise.userservice.dto.PaymentCardResponse;
import com.innowise.userservice.dto.PaymentCardUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentCardService {
    PaymentCardResponse create(PaymentCardCreateRequest request);
    PaymentCardResponse getById(Long id);
    Page<PaymentCardResponse> getAll(String name, String surname, Pageable pageable);
    List<PaymentCardResponse> getByUserId(Long userId);
    PaymentCardResponse update(Long id, PaymentCardUpdateRequest request);
    void activate(Long id);
    void deactivate(Long id);
    void delete(Long id);
    boolean isCardOwner(Long cardId, Long userId);
}