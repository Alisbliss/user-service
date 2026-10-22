package com.innowise.userservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCardCreateRequest {
    @NotNull
    private Long userId;

    @NotBlank
    @Size(max = 19)
    private String number;

    @Size(max = 100)
    private String holder;

    @NotNull
    @Future
    private LocalDate expirationDate;

    private Boolean active;
}
