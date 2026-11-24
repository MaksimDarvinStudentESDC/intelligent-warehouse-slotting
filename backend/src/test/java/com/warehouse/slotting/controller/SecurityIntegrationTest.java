package com.warehouse.slotting.controller;
import com.warehouse.slotting.config.SecurityConfig;
import com.warehouse.slotting.security.CustomUserDetailsService;
import com.warehouse.slotting.dto.auth.*;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.UserRepository;
import com.warehouse.slotting.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={AuthController.class,CatalogController.class,LocationController.class,ClassificationController.class,HardConstraintsController.class,RecommendationController.class})
@Import({SecurityConfig.class, CustomUserDetailsService.class})
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AuthService auth;
    @MockitoBean CatalogService catalog;
    @MockitoBean LocationService locations;
    @MockitoBean ClassificationService classification;
    @MockitoBean HardConstraintsService constraints;
    @MockitoBean RecommendationService recommendations;
    record Endpoint(String method,String path,Set<UserRole> allowed) {}
    static Stream<Arguments> matrix() {
        var warehouse=Set.of(UserRole.operator,UserRole.manager,UserRole.planner);
        var op=Set.of(UserRole.operator); var pl=Set.of(UserRole.planner);
        var endpoints=List.of(new Endpoint("POST","/api/products",pl),new Endpoint("POST","/api/batches",op),new Endpoint("GET","/api/warehouses/1/batches",warehouse),new Endpoint("GET","/api/warehouses/1/locations",warehouse),new Endpoint("GET","/api/constraints/batches/1/locations/1",op),new Endpoint("GET","/api/recommendations/batches/1",op),new Endpoint("POST","/api/classifications/run",pl),new Endpoint("GET","/api/auth/me",EnumSet.allOf(UserRole.class)),new Endpoint("GET","/api/rbac-test-unknown",Set.of()));
        return Arrays.stream(UserRole.values()).flatMap(role->endpoints.stream().map(e->Arguments.of(role,e.method(),e.path(),e.allowed().contains(role)?(e.method().equals("POST")?400:200):403)));
    }
    private void account(UserRole role,boolean active) {
        var user=new User("test","Test","hash",role); user.setActive(active);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token").header("alg","HS256").subject("1").claim("role","ADMIN").build());
    }
    @ParameterizedTest @MethodSource("matrix") void checksMatrix(UserRole role,String method,String path,int expected) throws Exception {
        account(role,true);
        when(auth.getCurrentUser(1L)).thenReturn(new UserResponse(1L,"test","Test",role));
        when(recommendations.recommend(1L,5,"nearest_feasible")).thenReturn(new com.warehouse.slotting.dto.RecommendationResponse(1L,"nearest_feasible",0,List.of()));
        var request=request(HttpMethod.valueOf(method),path).header("Authorization","Bearer test-token");
        if (method.equals("POST")) {
            request.contentType("application/json").content("{}");
        }
        mvc.perform(request).andExpect(status().is(expected));
    }
    @Test void noToken() throws Exception { mvc.perform(get("/api/warehouses/1/locations")).andExpect(status().isUnauthorized()); }
    @Test void invalidToken() throws Exception { when(decoder.decode("bad")).thenThrow(new BadJwtException("invalid")); mvc.perform(get("/api/auth/me").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized()); }
    @Test void disabledAccount() throws Exception { account(UserRole.operator,false); mvc.perform(get("/api/auth/me").header("Authorization","Bearer test-token")).andExpect(status().isUnauthorized()); }
    @Test void missingAccount() throws Exception { account(UserRole.operator,true); when(users.findById(1L)).thenReturn(Optional.empty()); mvc.perform(get("/api/auth/me").header("Authorization","Bearer test-token")).andExpect(status().isUnauthorized()); }
    @Test void publicLogin() throws Exception {
        when(auth.login(any())).thenReturn(new LoginResponse("token","Bearer",1800,new UserResponse(1L,"test","Test",UserRole.operator)));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"login\":\"test\",\"password\":\"password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("token")).andExpect(jsonPath("$.user.role").value("operator")).andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }
    @Test void invalidCredentials() throws Exception { when(auth.login(any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)); mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"login\":\"test\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized()); }
    @Test void invalidLoginBody() throws Exception { mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isBadRequest()); verifyNoInteractions(auth); }
}
