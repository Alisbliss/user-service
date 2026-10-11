package com.innowise.userservice.service;

import com.innowise.userservice.dto.UserCreateRequest;
import com.innowise.userservice.dto.UserResponse;
import com.innowise.userservice.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse create(UserCreateRequest request);
    UserResponse getById(Long id);
    Page<UserResponse> getAll(String name, String surname, Pageable pageable);
    UserResponse update(Long id, UserUpdateRequest request);
    void activate(Long id);
    void deactivate(Long id);
    void delete(Long id);
}
