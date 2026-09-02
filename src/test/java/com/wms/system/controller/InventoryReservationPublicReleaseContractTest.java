package com.wms.system.controller;

import com.wms.system.service.InventoryReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationPublicReleaseContractTest {

    @Test
    void controllerHasNoPublicWriteMapping() {
        boolean exposesPost = Arrays.stream(InventoryReservationController.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(mapping -> mapping != null)
            .flatMap(mapping -> Arrays.stream(mapping.method()))
            .anyMatch(RequestMethod.POST::equals);

        assertThat(exposesPost).isFalse();
    }

    @Test
    void serviceKeepsOnlyOrderScopedReleaseAsPublicApi() {
        assertThat(publicMethodNamed("releaseReservation")).isFalse();
        assertThat(publicMethodNamed("releaseOpenReservationsForOrder")).isTrue();
    }

    @Test
    void remainingQueriesRequireInventoryViewAuthority() {
        Arrays.stream(InventoryReservationController.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("listBy"))
            .forEach(method -> {
                PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                    method,
                    PreAuthorize.class
                );
                assertThat(authorization).isNotNull();
                assertThat(authorization.value()).contains("inventory:view", "TENANT_ADMIN");
            });
    }

    private boolean publicMethodNamed(String name) {
        return Arrays.stream(InventoryReservationService.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(name))
            .map(Method::getModifiers)
            .anyMatch(Modifier::isPublic);
    }
}
