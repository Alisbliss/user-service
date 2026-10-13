package com.innowise.userservice;

import com.innowise.userservice.dto.PaymentCardCreateRequest;
import com.innowise.userservice.dto.UserCreateRequest;
import com.innowise.userservice.repository.PaymentCardRepository;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("resource")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class PaymentCardFlowIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("userdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PaymentCardRepository cardRepository;

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        cardRepository.deleteAll();
        userRepository.deleteAll();

        UserCreateRequest userRequest = new UserCreateRequest();
        userRequest.setName("Alex");
        userRequest.setSurname("Aleksandrov");
        userRequest.setBirthDate(LocalDate.of(1989, 4, 8));
        userRequest.setEmail("alex.aleksandrov@example.com");
        userRequest.setActive(true);

        String json = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andReturn().getResponse().getContentAsString();

        userId = objectMapper.readTree(json).get("id").asLong();
    }

    //full flow
    @Test
    void fullCardFlow_shouldCreateGetAndDelete() throws Exception {
        PaymentCardCreateRequest card = new PaymentCardCreateRequest();
        card.setUserId(userId);
        card.setNumber("4276123456789012");
        card.setHolder("ALEX ALEKSANDROV");
        card.setExpirationDate(LocalDate.of(2029, 8, 31));
        card.setActive(true);

        //CREATE-201
        String json = mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(card)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("4276123456789012"))
                .andReturn().getResponse().getContentAsString();

        Long cardId = objectMapper.readTree(json).get("id").asLong();

        //GET by id-200
        mockMvc.perform(get("/api/cards/{id}", cardId))
                .andExpect(status().isOk());

        //GET cards by user-200
        mockMvc.perform(get("/api/cards/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        //DELETE-204
        mockMvc.perform(delete("/api/cards/{id}", cardId))
                .andExpect(status().isNoContent());
    }

    //limit
    @Test
    void createCard_shouldReturn400WhenLimitExceeded() throws Exception {
        String[] numbers = {
                "4276123456781001",
                "4276123456781002",
                "4276123456781003",
                "4276123456781004",
                "4276123456781005"};

        for (String number : numbers) {
            PaymentCardCreateRequest req = new PaymentCardCreateRequest();
            req.setUserId(userId);
            req.setNumber(number);
            req.setHolder("ALEX ALEKSANDROV");
            req.setExpirationDate(LocalDate.of(2030, 8, 31));
            req.setActive(true);

            mockMvc.perform(post("/api/cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        PaymentCardCreateRequest sixth = new PaymentCardCreateRequest();
        sixth.setUserId(userId);
        sixth.setNumber("4276123456781006");
        sixth.setHolder("ALEX ALEKSANDROV");
        sixth.setExpirationDate(LocalDate.of(2030, 8, 31));
        sixth.setActive(true);

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sixth)))
                .andExpect(status().isUnprocessableContent());
    }
}
