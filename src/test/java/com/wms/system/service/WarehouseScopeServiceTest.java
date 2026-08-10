package com.wms.system.service;

import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseScopeServiceTest {

    @Mock
    private UserWarehouseService userWarehouseService;
    @InjectMocks
    private WarehouseScopeService service;

    @Test
    void activatedWarehouseStaffSeesOnlyFullyAuthorizedResources() {
        Authentication authentication = authentication("WAREHOUSE_STAFF");
        when(userWarehouseService.getAssignedWarehouseIds(12L)).thenReturn(Set.of(1L, 2L));
        List<Resource> resources = List.of(
            new Resource(10L, List.of(1L)),
            new Resource(11L, List.of(1L, 2L)),
            new Resource(12L, List.of(2L, 3L)),
            new Resource(13L, List.of())
        );

        List<Resource> visible = service.filterAccessible(authentication, resources, Resource::warehouseIds);

        assertThat(visible).extracting(Resource::id).containsExactly(10L, 11L);
    }

    @Test
    void forgedTaskOutsideScopeIsForbidden() {
        Authentication authentication = authentication("WAREHOUSE_STAFF");
        when(userWarehouseService.getAssignedWarehouseIds(12L)).thenReturn(Set.of(1L));

        assertThatThrownBy(() -> service.requireAccess(
            authentication, List.of(2L), "OUTBOUND_TASK", 99L
        )).isInstanceOfSatisfying(BusinessException.class, error ->
            assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.WAREHOUSE_SCOPE_DENIED));
    }

    @Test
    void assignedTaskIsAllowed() {
        Authentication authentication = authentication("WAREHOUSE_STAFF");
        when(userWarehouseService.getAssignedWarehouseIds(12L)).thenReturn(Set.of(1L, 2L));

        assertThatCode(() -> service.requireAccess(
            authentication, List.of(1L, 2L), "INBOUND_ORDER", 44L
        )).doesNotThrowAnyException();
    }

    @Test
    void warehouseAdminRemainsUnscoped() {
        Authentication authentication = authentication("WAREHOUSE_ADMIN");
        List<Resource> resources = List.of(new Resource(10L, List.of(3L)));

        assertThat(service.filterAccessible(authentication, resources, Resource::warehouseIds))
            .isSameAs(resources);
        assertThatCode(() -> service.requireAccess(
            authentication, List.of(3L), "STOCKTAKE_TASK", 10L
        )).doesNotThrowAnyException();
        verify(userWarehouseService, never()).getAssignedWarehouseIds(12L);
    }

    private Authentication authentication(String roleCode) {
        User user = User.builder()
            .id(12L)
            .username("operator")
            .password("encoded")
            .enabled(true)
            .build();
        return new UsernamePasswordAuthenticationToken(
            new SecurityUser(user),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + roleCode))
        );
    }

    private record Resource(Long id, List<Long> warehouseIds) {
    }
}
