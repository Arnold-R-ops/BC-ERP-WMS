package com.wms.system.dto;

import com.wms.system.controller.WarehouseController;
import com.wms.system.dto.inbound.ConfirmOrderRequest;
import com.wms.system.dto.inbound.CreateInboundOrderRequest;
import com.wms.system.dto.inbound.ReceiveGoodsRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InboundRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createRequestRejectsMissingTargetLocation() {
        CreateInboundOrderRequest.InboundOrderItemRequest item =
            CreateInboundOrderRequest.InboundOrderItemRequest.builder()
                .productSkuId(1L)
                .planQty(10)
                .targetWarehouseId(1L)
                .build();
        CreateInboundOrderRequest request = CreateInboundOrderRequest.builder()
            .supplierId(1L)
            .items(List.of(item))
            .build();

        assertThat(paths(validator.validate(request)))
            .contains("items[0].targetLocationId");
    }

    @Test
    void confirmRequestRejectsMissingTargetWarehouseAndLocation() {
        ConfirmOrderRequest.ItemConfirmation confirmation =
            ConfirmOrderRequest.ItemConfirmation.builder()
                .itemId(1L)
                .confirmedQty(10)
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();
        ConfirmOrderRequest request = ConfirmOrderRequest.builder()
            .confirmations(List.of(confirmation))
            .build();

        assertThat(paths(validator.validate(request)))
            .contains("confirmations[0].targetWarehouseId", "confirmations[0].targetLocationId");
    }

    @Test
    void receiveRequestValidatesNestedLocationId() {
        ReceiveGoodsRequest.ItemReceipt receipt = ReceiveGoodsRequest.ItemReceipt.builder()
            .itemId(1L)
            .actualQty(10)
            .build();
        ReceiveGoodsRequest request = ReceiveGoodsRequest.builder()
            .receipts(List.of(receipt))
            .build();

        assertThat(paths(validator.validate(request)))
            .contains("receipts[0].locationId");
    }

    @Test
    void warehouseCodeCannotExceedLocationCompatibleLength() {
        WarehouseController.CreateWarehouseRequest request =
            new WarehouseController.CreateWarehouseRequest(
                "WH-123456789012345678",
                "Test Warehouse",
                null,
                null,
                null
            );

        assertThat(paths(validator.validate(request))).contains("code");
    }

    private Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
