package com.innowise.userservice.repository.specification;

import com.innowise.userservice.entity.PaymentCard;
import com.innowise.userservice.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentCardSpecification {
    private PaymentCardSpecification() {}

    public static Specification<PaymentCard> userNameLike(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.isBlank()) return criteriaBuilder.conjunction();
            Join<PaymentCard, User> user = root.join("user");
            return criteriaBuilder.like(criteriaBuilder.lower(user.get("name")),
                    "%" + name.toLowerCase() + "%");};
    }

    public static Specification<PaymentCard> userSurnameLike(String surname) {
        return (root, query, criteriaBuilder) -> {
            if (surname == null || surname.isBlank()) return criteriaBuilder.conjunction();
            Join<PaymentCard, User> user = root.join("user");
            return criteriaBuilder.like(criteriaBuilder.lower(user.get("surname")),
                    "%" + surname.toLowerCase() + "%");};
    }
}
