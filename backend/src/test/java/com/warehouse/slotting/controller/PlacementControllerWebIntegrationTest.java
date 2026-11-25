package com.warehouse.slotting.controller;
import com.warehouse.slotting.config.SecurityConfig;
import com.warehouse.slotting.dto.*;
import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.repository.UserRepository;
import com.warehouse.slotting.service.PlacementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(PlacementController.class)
@Import(SecurityConfig.class)
class PlacementControllerWebIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PlacementService service;
    private static final String BODY = "{\"batchId\":1,\"locationId\":6,\"locationVersion\":0}";
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/placements/confirm").with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))).contentType("application/json").content(body);
    }
    @Test void confirms() throws Exception {
        when(service.confirm(new PlacementRequest(1L,6L,0L),2L)).thenReturn(new PlacementResponse(10L,1L,6L,"ST-06","placed",BigDecimal.TEN,BigDecimal.ONE,OffsetDateTime.parse("2026-11-25T12:00:00Z")));
        mvc.perform(request(BODY)).andExpect(status().isCreated()).andExpect(jsonPath("$.movementId").value(10)).andExpect(jsonPath("$.status").value("placed"));
        verify(service).confirm(new PlacementRequest(1L,6L,0L),2L);
    }
    @Test void rejectsInvalid() throws Exception { mvc.perform(request("{}")).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
    @Test void stale() throws Exception {
        when(service.confirm(any(),eq(2L))).thenThrow(new UseCaseException(UseCaseException.Kind.CONFLICT,"STALE_RECOMMENDATION"));
        mvc.perform(request(BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("STALE_RECOMMENDATION"));
    }
    @Test void anonymous() throws Exception { mvc.perform(post("/api/placements/confirm").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized()); }
}
