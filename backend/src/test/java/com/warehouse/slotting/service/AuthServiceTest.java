package com.warehouse.slotting.service;
import com.warehouse.slotting.dto.auth.*;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.UserRepository;
import com.warehouse.slotting.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthServiceTest {
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final UserRepository repository = mock(UserRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(manager, repository, jwt);
    private final LoginRequest request = new LoginRequest("test", "password");
    private User user() { var u = new User("test", "Test", "hash", UserRole.operator); ReflectionTestUtils.setField(u,"id",1L); return u; }
    @Test void successfulLogin() {
        var u = user(); when(repository.findByLoginIgnoreCase("test")).thenReturn(Optional.of(u));
        when(jwt.generateToken(u)).thenReturn("token"); when(jwt.getExpirationSeconds()).thenReturn(1800L);
        var response = service.login(request);
        assertEquals("token", response.accessToken()); assertEquals("Bearer", response.tokenType()); assertEquals(1800L,response.expiresIn()); assertEquals(1L,response.user().id());
        verify(manager).authenticate(new UsernamePasswordAuthenticationToken("test","password"));
    }
    @Test void wrongCredentials() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.login(request)).getStatusCode().value()); verifyNoInteractions(jwt);
    }
    @Test void disabledAccount() {
        var u=user(); u.setActive(false); when(repository.findByLoginIgnoreCase("test")).thenReturn(Optional.of(u));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.login(request)).getStatusCode().value()); verifyNoInteractions(jwt);
    }
    @Test void oversizedUtf8Password() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.login(new LoginRequest("test", "é".repeat(37)))).getStatusCode().value()); verifyNoInteractions(manager,jwt);
    }
    @Test void currentUser() { when(repository.findById(1L)).thenReturn(Optional.of(user())); assertEquals(UserRole.operator,service.getCurrentUser(1L).role()); }
    @Test void missingCurrentUser() { assertEquals(401,assertThrows(ResponseStatusException.class,()->service.getCurrentUser(1L)).getStatusCode().value()); }
}
