package com.wms.system.dto;

import com.wms.system.dto.receivinginspection.*;
import com.wms.system.dto.rma.*;
import com.wms.system.dto.transfer.*;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiNestedSchemaNameContractTest {

    @Test
    void businessNestedRecordsExposeUniqueGlobalSchemaNames() {
        List<Class<?>> containers = List.of(
            CreateTransferRequest.class, DispatchTransferRequest.class, ReceiveTransferRequest.class,
            TransferResponse.class, TransferWarehouseTaskResponse.class,
            CreateRmaRequest.class, ReceiveRmaRequest.class, InspectRmaRequest.class,
            SetRmaRefundEligibilityRequest.class, RmaResponse.class, RmaWarehouseTaskResponse.class,
            SubmitReceivingInspectionRequest.class, ReceivingInspectionTaskResponse.class,
            ReceivingInspectionResultResponse.class
        );

        List<String> names = containers.stream()
            .flatMap(container -> Arrays.stream(container.getDeclaredClasses()))
            .filter(Class::isRecord)
            .map(type -> type.getAnnotation(Schema.class))
            .map(annotation -> annotation == null ? "" : annotation.name())
            .toList();

        assertThat(names).isNotEmpty().allSatisfy(name -> assertThat(name).isNotBlank());
        assertThat(new LinkedHashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    void shortageClosureConfirmationIsRequiredAndOnlyAcceptsTrue() throws Exception {
        Schema schema = CloseTransferShortageRequest.class.getDeclaredMethod("confirmed")
            .getAnnotation(Schema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(schema.allowableValues()).containsExactly("true");
    }
}
