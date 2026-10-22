package com.innowise.userservice.repository.specification;

import com.innowise.userservice.entity.User;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecification {
    private UserSpecification() {}

    public static Specification<User> nameLike(String name) {
        return (root, query, criteriaBuilder)
                -> name == null || name.isBlank()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.like(criteriaBuilder.lower(root.get("name")),
                "%" + name.toLowerCase() + "%");
    }

    public static Specification<User> surnameLike(String surname) {
        return (root, query, criteriaBuilder)
                -> surname == null || surname.isBlank()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.like(criteriaBuilder.lower(root.get("surname")),
                "%" + surname.toLowerCase() + "%");
    }
}
