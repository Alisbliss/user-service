package com.innowise.userservice;

import com.innowise.userservice.dto.UserCreateRequest;
import com.innowise.userservice.dto.UserResponse;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("resource")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class UserFlowIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("userdb")
            .withUsername("test")
            .withPassword("test");
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "redis");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private UserRepository userRepository;
    @Autowired
    private CacheManager cacheManager;
    private Cache usersCache;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        Cache cache = Objects.requireNonNull(cacheManager.getCache("users"));
        cache.clear();
        this.usersCache = cache;
    }

    private UserCreateRequest createRequest() {
        UserCreateRequest request = new UserCreateRequest();
        request.setName("Alex");
        request.setSurname("Aleksandrov");
        request.setBirthDate(LocalDate.of(1989, 4, 8));
        request.setEmail("alex.aleksandrov@example.com");
        request.setActive(true);
        return request;
    }

    //full flow
    @Test
    void fullUserFlow_shouldCreateGetUpdateDelete() throws Exception {
        UserCreateRequest request = createRequest();

        //CREATE-201
        String json = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alex.aleksandrov@example.com"))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(json).get("id").asLong();

        //GET-200
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex"))
                .andExpect(jsonPath("$.surname").value("Aleksandrov"));

        assertThat(usersCache.get(id)).isNotNull();

        //UPDATE-200
        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kostya\",\"surname\":\"Kostya\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kostya"));

        assertThat(userRepository.findById(id)).isPresent();
        assertThat(userRepository.findById(id).orElseThrow().getName()).isEqualTo("Kostya");

        Cache.ValueWrapper cached = Objects.requireNonNull(usersCache.get(id), "Cache entry missing");
        UserResponse cachedUser = (UserResponse) Objects.requireNonNull(cached.get(), "Cached value missing");
        assertThat(cachedUser.getName()).isEqualTo("Kostya");

        //DELETE-204
        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(usersCache.get(id)).isNull();
    }

    //error cases
    @Test
    void createUser_shouldReturn409WhenEmailDuplicate() throws Exception {
        UserCreateRequest request = createRequest();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void getUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 2))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_shouldReturn400WhenValidationFails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"surname\":\"\",\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    //cache case
    @Test
    void getById_shouldReturnFromCacheOnSecondCall() throws Exception {
        UserCreateRequest request = createRequest();
        String json = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(json).get("id").asLong();
        Mockito.reset(userRepository);

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk());
        verify(userRepository, times(1)).findById(id);

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk());
        verify(userRepository, times(1)).findById(id);
    }
}
