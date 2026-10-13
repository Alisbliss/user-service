package com.innowise.userservice.service;

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
import com.innowise.userservice.service.impl.PaymentCardServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceImplTest {
    @Mock
    private PaymentCardRepository cardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentCardMapper cardMapper;
    @InjectMocks
    private PaymentCardServiceImpl cardService;

    private PaymentCardCreateRequest createRequest() {
        PaymentCardCreateRequest request = new PaymentCardCreateRequest();
        request.setUserId(1L);
        request.setNumber("4276123456789012");
        request.setHolder("ALEX ALEKSANDROV");
        request.setExpirationDate(LocalDate.of(2030, 8, 31));
        request.setActive(true);
        return request;
    }

    //create
    @Test
    void create_shouldSaveCardWhenLimitNotReached() {
        PaymentCardCreateRequest request = createRequest();
        User user = new User();
        user.setId(1L);
        PaymentCard card = new PaymentCard();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserId(1L)).thenReturn(3L);
        when(cardRepository.existsByNumber(request.getNumber())).thenReturn(false);
        when(cardMapper.toEntity(request)).thenReturn(card);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toResponse(card)).thenReturn(new PaymentCardResponse());
        cardService.create(request);

        assertThat(card.getUser()).isSameAs(user);
        verify(cardRepository).save(card);
    }

    @Test
    void create_shouldThrowBusinessWhenLimitReached() {
        PaymentCardCreateRequest request = createRequest();
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> cardService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 cards");
    }

    @Test
    void create_shouldThrowConflictWhenNumberExists() {
        PaymentCardCreateRequest request = createRequest();
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserId(1L)).thenReturn(0L);
        when(cardRepository.existsByNumber(request.getNumber())).thenReturn(true);

        assertThatThrownBy(() -> cardService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    //getById
    @Test
    void getById_shouldReturn() {
        PaymentCard card = new PaymentCard();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardMapper.toResponse(card)).thenReturn(new PaymentCardResponse());

        assertThat(cardService.getById(1L)).isNotNull();
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(cardRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getById(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    //getAll
    @Test
    void getAll_shouldReturnPage() {
        Pageable pageable = PageRequest.of(0, 10);
        PaymentCard card = new PaymentCard();
        Page<PaymentCard> page = new PageImpl<>(List.of(card), pageable, 1);
        when(cardRepository.findAll(ArgumentMatchers.<Specification<PaymentCard>>any(), eq(pageable))).thenReturn(page);
        when(cardMapper.toResponse(card)).thenReturn(new PaymentCardResponse());

        assertThat(cardService.getAll(null, null, pageable).getTotalElements()).isEqualTo(1);
    }

    //getByUserId
    @Test
    void getByUserId_shouldReturnList() {
        PaymentCard card = new PaymentCard();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(cardRepository.findByUserId(1L)).thenReturn(List.of(card));
        when(cardMapper.toResponse(card)).thenReturn(new PaymentCardResponse());

        assertThat(cardService.getByUserId(1L)).hasSize(1);
    }

    @Test
    void getByUserId_shouldThrowWhenUserNotFound() {
        when(userRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> cardService.getByUserId(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    //update
    @Test
    void update_shouldUpdate() {
        PaymentCard card = new PaymentCard();
        card.setNumber("4276123456789012");
        PaymentCardUpdateRequest request = new PaymentCardUpdateRequest();
        request.setNumber("5536912345678901");
        request.setHolder("ALEX ALEKSANDROV");
        request.setExpirationDate(LocalDate.of(2031, 12, 31));
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.existsByNumber("5536912345678901")).thenReturn(false);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toResponse(card)).thenReturn(new PaymentCardResponse());
        cardService.update(1L, request);

        verify(cardMapper).updateEntity(request, card);
    }

    @Test
    void update_shouldThrowConflictWhenNumberTaken() {
        PaymentCard card = new PaymentCard();
        card.setNumber("4276123456789012");
        PaymentCardUpdateRequest request = new PaymentCardUpdateRequest();
        request.setNumber("5536912345678901");
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.existsByNumber("5536912345678901")).thenReturn(true);

        assertThatThrownBy(() -> cardService.update(1L, request))
                .isInstanceOf(ConflictException.class);
    }

    //activate
    @Test
    void activate_shouldSetTrue() {
        PaymentCard card = new PaymentCard();
        card.setActive(false);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        cardService.activate(1L);

        assertThat(card.getActive()).isTrue();
    }

    //deactivate
    @Test
    void deactivate_shouldSetFalse() {
        PaymentCard card = new PaymentCard();
        card.setActive(true);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        cardService.deactivate(1L);

        assertThat(card.getActive()).isFalse();
    }

    //delete
    @Test
    void delete_shouldRemove() {
        PaymentCard card = new PaymentCard();
        when(cardRepository.existsById(1L)).thenReturn(true);
        cardService.delete(1L);

        verify(cardRepository).deleteById(1L);
    }
}
