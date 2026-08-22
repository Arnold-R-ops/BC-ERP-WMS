package com.wms.system.service;

import com.wms.system.entity.User;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityVersionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SysRoleInheritRepository roleInheritRepository;

    @InjectMocks
    private SecurityVersionService securityVersionService;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdAndCompanyId(anyLong(), eq(1L)))
            .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        lenient().when(roleInheritRepository.findChildRoleIdsByCompanyIdAndParentRoleId(
                eq(1L), anyLong()))
            .thenAnswer(invocation -> roleInheritRepository
                .findChildRoleIdsByParentRoleId(invocation.getArgument(1)));
        lenient().when(userRoleRepository.findUserIdsByCompanyIdAndRoleId(eq(1L), anyLong()))
            .thenAnswer(invocation -> userRoleRepository.findUserIdsByRoleId(invocation.getArgument(1)));
        lenient().when(userRepository.findAllByCompanyIdAndIdIn(eq(1L), any()))
            .thenAnswer(invocation -> userRepository.findAllById((Iterable<Long>) invocation.getArgument(1)));
        lenient().when(userRepository.findAllByCompanyId(1L))
            .thenAnswer(invocation -> userRepository.findAll());
    }

    @Test
    void bumpTreatsNullVersionAsZero() {
        User user = User.builder().id(1L).securityVersion(null).build();

        long nextVersion = securityVersionService.bump(user);

        assertThat(nextVersion).isEqualTo(1L);
        assertThat(user.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void bumpForUserAdvancesAndPersistsVersion() {
        User user = User.builder().id(1L).securityVersion(7L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        long nextVersion = securityVersionService.bumpForUser(1L);

        assertThat(nextVersion).isEqualTo(8L);
        assertThat(user.getSecurityVersion()).isEqualTo(8L);
        verify(userRepository).save(user);
    }

    @Test
    void bumpForUserRejectsMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityVersionService.bumpForUser(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    void bumpForRoleAndDescendantsTraversesGraphAndDeduplicatesUsers() {
        when(roleInheritRepository.findChildRoleIdsByParentRoleId(10L))
                .thenReturn(Set.of(20L, 30L));
        when(roleInheritRepository.findChildRoleIdsByParentRoleId(20L))
                .thenReturn(Set.of(40L));
        when(roleInheritRepository.findChildRoleIdsByParentRoleId(30L))
                .thenReturn(Set.of(40L));
        when(roleInheritRepository.findChildRoleIdsByParentRoleId(40L))
                .thenReturn(Set.of());

        when(userRoleRepository.findUserIdsByRoleId(10L)).thenReturn(Set.of(1L));
        when(userRoleRepository.findUserIdsByRoleId(20L)).thenReturn(Set.of(2L, 3L));
        when(userRoleRepository.findUserIdsByRoleId(30L)).thenReturn(Set.of(3L, 4L));
        when(userRoleRepository.findUserIdsByRoleId(40L)).thenReturn(Set.of(5L));

        List<User> users = List.of(
                User.builder().id(1L).securityVersion(1L).build(),
                User.builder().id(2L).securityVersion(2L).build(),
                User.builder().id(3L).securityVersion(3L).build(),
                User.builder().id(4L).securityVersion(4L).build(),
                User.builder().id(5L).securityVersion(5L).build()
        );
        when(userRepository.findAllById(Set.of(1L, 2L, 3L, 4L, 5L))).thenReturn(users);

        securityVersionService.bumpForRoleAndDescendants(10L);

        assertThat(users)
                .extracting(User::getSecurityVersion)
                .containsExactly(2L, 3L, 4L, 5L, 6L);
        verify(roleInheritRepository, times(1)).findChildRoleIdsByParentRoleId(40L);
        verify(userRepository).saveAll(users);
    }

    @Test
    void bumpForRoleAndDescendantsSkipsPersistenceWhenNoUsersAreAffected() {
        when(roleInheritRepository.findChildRoleIdsByParentRoleId(10L)).thenReturn(Set.of());
        when(userRoleRepository.findUserIdsByRoleId(10L)).thenReturn(Set.of());

        securityVersionService.bumpForRoleAndDescendants(10L);

        verify(userRepository, never()).findAllById(any());
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void bumpAllUsersAdvancesEveryLoadedUser() {
        List<User> users = List.of(
                User.builder().id(1L).securityVersion(4L).build(),
                User.builder().id(2L).securityVersion(null).build()
        );
        when(userRepository.findAll()).thenReturn(users);

        securityVersionService.bumpAllUsers();

        assertThat(users)
                .extracting(User::getSecurityVersion)
                .containsExactly(5L, 1L);
        verify(userRepository).saveAll(users);
    }
}
