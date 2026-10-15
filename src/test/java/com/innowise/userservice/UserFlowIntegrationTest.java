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

    private Long createUser(UserCreateRequest request) throws Exception {
        String json = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
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

        //assert: DB updated
        assertThat(userRepository.findById(id)).isPresent();
        assertThat(userRepository.findById(id).orElseThrow().getName()).isEqualTo("Kostya");

        //assert: cache refreshed by @CachePut
        Cache.ValueWrapper cached = Objects.requireNonNull(usersCache.get(id), "Cache entry missing");
        UserResponse cachedUser = (UserResponse) Objects.requireNonNull(cached.get(), "Cached value missing");
        assertThat(cachedUser.getName()).isEqualTo("Kostya");

        //DELETE-204
        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());

        //assert: removed from DB and cache
        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(usersCache.get(id)).isNull();
    }

    //error cases
    //POST-409
    @Test
    void createUser_shouldReturn409WhenEmailDuplicate() throws Exception {
        //CREATE-201
        createUser(createRequest());

        //POST duplicate-409
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isConflict());
    }

    //GET-404
    @Test
    void getUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 2))
                .andExpect(status().isNotFound());
    }

    //POST-400
    @Test
    void createUser_shouldReturn400WhenValidationFails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"surname\":\"\",\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    //GET all-200
    @Test
    void getAll_shouldReturnPagedUsers() throws Exception {
        //CREATE-201
        createUser(createRequest());

        UserCreateRequest second = createRequest();
        second.setEmail("second@example.com");
        createUser(second);

        //GET all-200
        mockMvc.perform(get("/api/users")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    //GET filter by name-200
    @Test
    void getAll_shouldFilterByName() throws Exception {
        //CREATE-201
        createUser(createRequest());

        UserCreateRequest second = createRequest();
        second.setName("Ivan");
        second.setEmail("ivan@example.com");
        createUser(second);

        //GET filter-200
        mockMvc.perform(get("/api/users").param("name", "Alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alex"));
    }

    //GET filter by surname-200
    @Test
    void getAll_shouldFilterBySurname() throws Exception {
        //CREATE-201
        createUser(createRequest());

        UserCreateRequest second = createRequest();
        second.setSurname("Petrov");
        second.setEmail("petrov@example.com");
        createUser(second);

        //GET filter-200
        mockMvc.perform(get("/api/users").param("surname", "Petrov"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Petrov"));
    }

    //UPDATE-200
    @Test
    void updateUser_shouldReturn200() throws Exception {
        //CREATE-201
        Long id = createUser(createRequest());

        //UPDATE-200
        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    //UPDATE-404
    @Test
    void updateUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(put("/api/users/{id}", 9999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isNotFound());
    }

    //UPDATE email conflict-409
    @Test
    void updateUser_shouldReturn409WhenEmailTaken() throws Exception {
        //CREATE user1-201
        Long id1 = createUser(createRequest());

        //CREATE user2-201
        UserCreateRequest second = createRequest();
        second.setEmail("second@example.com");
        createUser(second);

        //UPDATE user1 with user2 email-409
        mockMvc.perform(put("/api/users/{id}", id1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"second@example.com\"}"))
                .andExpect(status().isConflict());
    }

    //ACTIVATE-204
    @Test
    void activateUser_shouldReturn204() throws Exception {
        //CREATE-201
        Long id = createUser(createRequest());

        //DEACTIVATE-204
        mockMvc.perform(patch("/api/users/{id}/deactivate", id))
                .andExpect(status().isNoContent());

        //ACTIVATE-204
        mockMvc.perform(patch("/api/users/{id}/activate", id))
                .andExpect(status().isNoContent());

        //GET after activate-200
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    //DEACTIVATE-204
    @Test
    void deactivateUser_shouldReturn204() throws Exception {
        //CREATE-201
        Long id = createUser(createRequest());

        //DEACTIVATE-204
        mockMvc.perform(patch("/api/users/{id}/deactivate", id))
                .andExpect(status().isNoContent());

        //GET after deactivate-200
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    //ACTIVATE-404
    @Test
    void activateUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/activate", 9))
                .andExpect(status().isNotFound());
    }

    //DEACTIVATE-404
    @Test
    void deactivateUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/deactivate", 9))
                .andExpect(status().isNotFound());
    }

    //cache case
    @Test
    void getById_shouldReturnFromCacheOnSecondCall() throws Exception {
        //CREATE-201
        Long id = createUser(createRequest());
        Mockito.reset(userRepository);

        //GET first-200 from DB
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk());
        verify(userRepository, times(1)).findById(id);

        //GET second-200 from cache
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk());
        verify(userRepository, times(1)).findById(id);
    }
}
