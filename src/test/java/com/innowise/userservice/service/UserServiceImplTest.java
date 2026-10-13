package com.innowise.userservice.service;

import com.innowise.userservice.dto.UserCreateRequest;
import com.innowise.userservice.dto.UserResponse;
import com.innowise.userservice.dto.UserUpdateRequest;
import com.innowise.userservice.entity.User;
import com.innowise.userservice.exception.ConflictException;
import com.innowise.userservice.exception.ResourceNotFoundException;
import com.innowise.userservice.mapper.UserMapper;
import com.innowise.userservice.repository.UserRepository;
import com.innowise.userservice.service.impl.UserServiceImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserServiceImpl userService;

    private UserCreateRequest createRequest() {
        UserCreateRequest request = new UserCreateRequest();
        request.setName("Alex");
        request.setSurname("Aleksandrov");
        request.setBirthDate(LocalDate.of(1989, 4, 8));
        request.setEmail("alex.aleksandrov@example.com");
        request.setActive(true);
        return request;
    }

    //create
    @Test
    void create_shouldSaveUserWhenEmailIsUnique() {
        UserCreateRequest request = createRequest();
        User entity = new User();
        UserResponse response = new UserResponse();
        response.setId(1L);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(entity);
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toResponse(entity)).thenReturn(response);
        UserResponse result = userService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository).save(entity);
    }

    @Test
    void create_shouldThrowConflictWhenEmailExists() {
        UserCreateRequest request = createRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already exists");
        verify(userRepository, never()).save(any());
    }

    //getById
    @Test
    void getById_shouldReturnUserWhenExists() {
        User entity = new User();
        UserResponse response = new UserResponse();
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity)).thenReturn(response);

        assertThat(userService.getById(1L)).isSameAs(response);
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User 2 not found");
    }

    //getAll
    @Test
    void getAll_shouldReturnPage() {
        Pageable pageable = PageRequest.of(0, 10);
        User entity = new User();
        Page<User> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), eq(pageable))).thenReturn(page);
        when(userMapper.toResponse(entity)).thenReturn(new UserResponse());
        Page<UserResponse> result = userService.getAll("Alex", "Aleksandrov", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void update_shouldUpdateUserWhenEmailIsFree() {
        User entity = new User();
        entity.setEmail("old.email@example.com");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("new.email@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userRepository.existsByEmail("new.email@example.com")).thenReturn(false);
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toResponse(entity)).thenReturn(new UserResponse());
        userService.update(1L, request);

        verify(userMapper).updateEntity(request, entity);
        verify(userRepository).save(entity);
    }

    @Test
    void update_shouldThrowConflictWhenEmailTakenByAnother() {
        User entity = new User();
        entity.setEmail("old.email@example.com");
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("occupied@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userRepository.existsByEmail("occupied@example.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    //activate
    @Test
    void activate_shouldSetActiveTrue() {
        User entity = new User();
        entity.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        userService.activate(1L);

        assertThat(entity.getActive()).isTrue();
        verify(userRepository).save(entity);
    }

    //deactivate
    @Test
    void deactivate_shouldSetActiveFalse() {
        User entity = new User();
        entity.setActive(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        userService.deactivate(1L);

        assertThat(entity.getActive()).isFalse();
        verify(userRepository).save(entity);
    }

    //delete
    @Test
    void delete_shouldRemoveUserWhenExists() {
        when(userRepository.existsById(1L)).thenReturn(true);
        userService.delete(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(userRepository.existsById(2L)).thenReturn(false);
        assertThatThrownBy(() -> userService.delete(2L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).deleteById(any());
    }
}
