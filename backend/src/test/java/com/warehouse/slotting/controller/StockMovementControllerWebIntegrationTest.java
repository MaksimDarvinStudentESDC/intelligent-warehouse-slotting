package com.warehouse.slotting.controller;
import com.warehouse.slotting.config.SecurityConfig;
import com.warehouse.slotting.repository.UserRepository;
import com.warehouse.slotting.service.StockMovementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(StockMovementController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles="MANAGER")
class StockMovementControllerWebIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean StockMovementService service;
    @Test void history() throws Exception {
        var from=OffsetDateTime.parse("2026-11-01T00:00:00Z");
        when(service.history(1L,2L,6L,from,null,0,20)).thenReturn(Page.empty(PageRequest.of(0,20)));
        mvc.perform(get("/api/warehouses/1/movements").param("productId","2").param("locationId","6").param("from",from.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.size").value(20));
        verify(service).history(1L,2L,6L,from,null,0,20);
    }
    @Test void invalidDate() throws Exception { mvc.perform(get("/api/warehouses/1/movements").param("from","invalid")).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
}
