package com.warehouse.slotting.security;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CustomUserDetailsServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(repository);
    @ParameterizedTest @EnumSource(UserRole.class) void mapsRole(UserRole role) {
        when(repository.findByLoginIgnoreCase("TEST")).thenReturn(Optional.of(new User("test", "Test", "hash", role)));
        var details = service.loadUserByUsername("TEST");
        assertEquals("test", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.isEnabled());
        assertEquals("ROLE_" + role.name().toUpperCase(Locale.ROOT), details.getAuthorities().iterator().next().getAuthority());
    }
    @Test void disabledAccount() {
        var user = new User("test", "Test", "hash", UserRole.operator); user.setActive(false);
        when(repository.findByLoginIgnoreCase("test")).thenReturn(Optional.of(user));
        assertFalse(service.loadUserByUsername("test").isEnabled());
    }
    @Test void missingAccount() { assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing")); }
}
