package com.wms.system.controller;

import com.wms.system.dto.receiving.PurchaseAsnResponse;
import com.wms.system.dto.receiving.UpdatePurchaseAsnDraftRequest;
import com.wms.system.entity.User;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.PurchaseAsnService;
import com.wms.system.service.UserWarehouseService;
import com.wms.system.service.WarehouseScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseAsnControllerTest {

    @Test
    void draftUpdateUsesDedicatedPutPermission() throws Exception {
        PreAuthorize authorization = PurchaseAsnController.class
            .getDeclaredMethod(
                "updateDraft",
                Long.class,
                UpdatePurchaseAsnDraftRequest.class,
                org.springframework.security.core.Authentication.class
            )
            .getAnnotation(PreAuthorize.class);

        org.assertj.core.api.Assertions.assertThat(authorization.value())
            .contains("purchase:asn:update")
            .doesNotContain("purchase:asn:create");
    }

    @Test
    void draftUpdateRequiresAccessToBothCurrentAndTargetWarehouse() {
        PurchaseAsnService asnService = mock(PurchaseAsnService.class);
        UserWarehouseService userWarehouseService = mock(UserWarehouseService.class);
        PurchaseAsnController controller = new PurchaseAsnController(
            asnService, new WarehouseScopeService(userWarehouseService));
        var authentication = warehouseStaffAuthentication();
        UpdatePurchaseAsnDraftRequest request = request(21L);
        when(asnService.get(60L)).thenReturn(response(20L));
        when(userWarehouseService.getAssignedWarehouseIds(9L)).thenReturn(Set.of(20L));

        assertThatThrownBy(() -> controller.updateDraft(60L, request, authentication))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                org.assertj.core.api.Assertions.assertThat(error.getErrorKey())
                    .isEqualTo(ErrorKeys.WAREHOUSE_SCOPE_DENIED));

        verify(asnService, never()).updateDraft(60L, request);
    }

    @Test
    void draftUpdateDelegatesAfterBothWarehouseChecksPass() {
        PurchaseAsnService asnService = mock(PurchaseAsnService.class);
        UserWarehouseService userWarehouseService = mock(UserWarehouseService.class);
        PurchaseAsnController controller = new PurchaseAsnController(
            asnService, new WarehouseScopeService(userWarehouseService));
        var authentication = warehouseStaffAuthentication();
        UpdatePurchaseAsnDraftRequest request = request(21L);
        PurchaseAsnResponse updated = response(21L);
        when(asnService.get(60L)).thenReturn(response(20L));
        when(userWarehouseService.getAssignedWarehouseIds(9L))
            .thenReturn(Set.of(20L, 21L));
        when(asnService.updateDraft(60L, request)).thenReturn(updated);

        controller.updateDraft(60L, request, authentication);

        verify(asnService).updateDraft(60L, request);
    }

    private TestingAuthenticationToken warehouseStaffAuthentication() {
        User user = User.builder()
            .id(9L).username("warehouse-user").password("unused").enabled(true).build();
        return new TestingAuthenticationToken(
            new SecurityUser(user),
            "unused",
            List.of(
                new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"),
                new SimpleGrantedAuthority("purchase:asn:update")
            )
        );
    }

    private UpdatePurchaseAsnDraftRequest request(Long warehouseId) {
        return new UpdatePurchaseAsnDraftRequest(
            3L, warehouseId, null, null, null, null, null,
            List.of(new UpdatePurchaseAsnDraftRequest.Item(
                40L, 2, null, null, null, null))
        );
    }

    private PurchaseAsnResponse response(Long warehouseId) {
        return new PurchaseAsnResponse(
            60L, 3L, "ASN-60", 10L, "PO-10", warehouseId, "WH" + warehouseId,
            PurchaseAsnStatus.DRAFT,
            null,
            null, null, null,
            null, null,
            null, null, null,
            null, null,
            List.of()
        );
    }
}
