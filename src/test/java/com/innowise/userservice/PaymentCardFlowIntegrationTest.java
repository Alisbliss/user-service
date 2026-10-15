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

    private PaymentCardCreateRequest buildCard(String number) {
        PaymentCardCreateRequest card = new PaymentCardCreateRequest();
        card.setUserId(userId);
        card.setNumber(number);
        card.setHolder("ALEX ALEKSANDROV");
        card.setExpirationDate(LocalDate.of(2030, 8, 31));
        card.setActive(true);
        return card;
    }

    private Long createCard(String number) throws Exception {
        String json = mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCard(number))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
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
    void createCard_shouldReturn422WhenLimitExceeded() throws Exception {
        String[] numbers = {
                "4276123456781001",
                "4276123456781002",
                "4276123456781003",
                "4276123456781004",
                "4276123456781005"};

        for (String number : numbers) {
            //CREATE 5 cards-201
            mockMvc.perform(post("/api/cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCard(number))))
                    .andExpect(status().isCreated());
        }

        //CREATE 6th card-422
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCard("4276123456781006"))))
                .andExpect(status().isUnprocessableContent());
    }

    //GET all-200
    @Test
    void getAllCards_shouldReturnPagedCards() throws Exception {
        //CREATE-201
        createCard("4276123456782001");

        //GET all-200
        mockMvc.perform(get("/api/cards")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    //GET filter by name-200
    @Test
    void getAllCards_shouldFilterByNameAndSurname() throws Exception {
        //CREATE-201
        createCard("4276123456782002");

        //GET filter-200
        mockMvc.perform(get("/api/cards")
                        .param("name", "Alex")
                        .param("surname", "Aleksandrov"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    //GET by id-404
    @Test
    void getCardById_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/cards/{id}", 9999))
                .andExpect(status().isNotFound());
    }

    //GET by user-404
    @Test
    void getCardsByUserId_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(get("/api/cards/user/{userId}", 9999))
                .andExpect(status().isNotFound());
    }

    //UPDATE-200
    @Test
    void updateCard_shouldReturn200() throws Exception {
        //CREATE-201
        Long cardId = createCard("4276123456782003");

        //UPDATE-200
        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holder\":\"NEW HOLDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder").value("NEW HOLDER"));
    }

    //UPDATE-404
    @Test
    void updateCard_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(put("/api/cards/{id}", 9999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holder\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    //UPDATE duplicate number-409
    @Test
    void updateCard_shouldReturn409WhenNumberTaken() throws Exception {
        //CREATE card1-201
        Long cardId1 = createCard("4276123456782004");

        //CREATE card2-201
        createCard("4276123456782005");

        //UPDATE card1 with card2 number-409
        mockMvc.perform(put("/api/cards/{id}", cardId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"4276123456782005\"}"))
                .andExpect(status().isConflict());
    }

    //ACTIVATE-204
    @Test
    void activateCard_shouldReturn204() throws Exception {
        //CREATE-201
        Long cardId = createCard("4276123456782006");

        //DEACTIVATE-204
        mockMvc.perform(patch("/api/cards/{id}/deactivate", cardId))
                .andExpect(status().isNoContent());

        //ACTIVATE-204
        mockMvc.perform(patch("/api/cards/{id}/activate", cardId))
                .andExpect(status().isNoContent());

        //GET after activate-200
        mockMvc.perform(get("/api/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    //DEACTIVATE-204
    @Test
    void deactivateCard_shouldReturn204() throws Exception {
        //CREATE-201
        Long cardId = createCard("4276123456782007");

        //DEACTIVATE-204
        mockMvc.perform(patch("/api/cards/{id}/deactivate", cardId))
                .andExpect(status().isNoContent());

        //GET after deactivate-200
        mockMvc.perform(get("/api/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    //ACTIVATE-404
    @Test
    void activateCard_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(patch("/api/cards/{id}/activate", 9999))
                .andExpect(status().isNotFound());
    }

    //DEACTIVATE-404
    @Test
    void deactivateCard_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(patch("/api/cards/{id}/deactivate", 9999))
                .andExpect(status().isNotFound());
    }
}
