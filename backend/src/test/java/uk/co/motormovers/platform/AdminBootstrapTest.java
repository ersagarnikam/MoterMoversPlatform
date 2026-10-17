package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.service.AdminBootstrap;
import uk.co.motormovers.platform.user.service.PasswordService;

class AdminBootstrapTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsTheFirstAdminAndEmailsALink() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(users.findByEmailIgnoreCase("ops@example.org")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));

        new AdminBootstrap(users, passwords, encoder, " Ops@Example.org ").run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ops@example.org");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        verify(passwords).sendInvite(saved.getValue(), "Motor Movers");
    }

    @Test
    void doesNothingOnceAnAdminExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        new AdminBootstrap(users, passwords, encoder, "ops@example.org").run(null);

        verify(users, never()).save(any());
        verify(passwords, never()).sendInvite(any(), anyString());
    }

    @Test
    void doesNothingWithoutAnEmail() {
        new AdminBootstrap(users, passwords, encoder, "").run(null);

        verify(users, never()).save(any());
    }
}
