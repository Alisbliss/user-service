package com.innowise.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
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
public class UserUpdateRequest {
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String surname;

    @Past
    private LocalDate birthDate;

    @Email
    private String email;

    private Boolean active;
}
