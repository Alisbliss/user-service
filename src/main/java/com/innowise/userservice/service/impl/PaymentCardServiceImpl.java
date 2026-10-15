package com.innowise.userservice.service.impl;

import com.innowise.userservice.dto.PaymentCardCreateRequest;
import com.innowise.userservice.dto.PaymentCardResponse;
import com.innowise.userservice.dto.PaymentCardUpdateRequest;
import com.innowise.userservice.entity.PaymentCard;
import com.innowise.userservice.entity.User;
import com.innowise.userservice.exception.BusinessException;
import com.innowise.userservice.exception.ConflictException;
import com.innowise.userservice.exception.ResourceNotFoundException;
import com.innowise.userservice.mapper.PaymentCardMapper;
import com.innowise.userservice.repository.PaymentCardRepository;
import com.innowise.userservice.repository.UserRepository;
import com.innowise.userservice.repository.specification.PaymentCardSpecification;
import com.innowise.userservice.service.PaymentCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentCardServiceImpl implements PaymentCardService {
    private static final long MAX_CARDS_PER_USER = 5;
    private static final String CARD_NOT_FOUND = "Card not found: ";
    private final PaymentCardRepository cardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper cardMapper;

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#request.userId")
    public PaymentCardResponse create(PaymentCardCreateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User " + request.getUserId() + " not found"));
        if (cardRepository.countByUserId(user.getId()) >= MAX_CARDS_PER_USER) {
            throw new BusinessException("User already has " + MAX_CARDS_PER_USER + " cards");
        }
        if (cardRepository.existsByNumber(request.getNumber())) {
            throw new ConflictException("Card number already exists");
        }
        PaymentCard card = cardMapper.toEntity(request);
        card.setUser(user);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentCardResponse getById(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CARD_NOT_FOUND + id));
        return cardMapper.toResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentCardResponse> getAll(String name, String surname, Pageable pageable) {
        Specification<PaymentCard> specification = Specification
                .where(PaymentCardSpecification.userNameLike(name))
                .and(PaymentCardSpecification.userSurnameLike(surname));
        return cardRepository.findAll(specification, pageable).map(cardMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentCardResponse> getByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User " + userId + " not found");
        }
        return cardRepository.findByUserId(userId).stream()
                .map(cardMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public PaymentCardResponse update(Long id, PaymentCardUpdateRequest request) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CARD_NOT_FOUND + id));
        if (request.getNumber() != null && !request.getNumber().equals(card.getNumber())
                && cardRepository.existsByNumber(request.getNumber())) {
            throw new ConflictException("Card number already in use");
        }
        cardMapper.updateEntity(request, card);
        return cardMapper.toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void activate(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CARD_NOT_FOUND + id));
        card.setActive(true);
        cardRepository.save(card);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void deactivate(Long id) {
        PaymentCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CARD_NOT_FOUND + id));
        card.setActive(false);
        cardRepository.save(card);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void delete(Long id) {
        if (!cardRepository.existsById(id)) {
            throw new ResourceNotFoundException(CARD_NOT_FOUND + id);
        }
        cardRepository.deleteById(id);
    }
}
