package com.wms.system.commercial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class CommercialDtos {
    private CommercialDtos() {}
    public record QuoteInput(@NotNull UUID requestKey, Long version, @NotNull Long customerId,
        @NotNull @FutureOrPresent LocalDate validUntil, @Size(max=255) String shippingAddress,
        @Size(max=2000) String terms, @NotEmpty @Size(max=200) List<@Valid LineInput> lines) {}
    public record LineInput(@NotNull Long productSkuId, @Min(1) @Max(1000000) int quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal unitPrice) {}
    public record InvoiceInput(@NotNull UUID requestKey, @NotNull Long salesOrderId,
        @NotNull LocalDate dueDate) {}
    public record ReceiptInput(@NotNull UUID requestKey, @NotNull @DecimalMin("0.01") @Digits(integer=13,fraction=2) BigDecimal amount,
        @NotNull @PastOrPresent LocalDate paymentDate, @NotNull PaymentMethod method,
        @Size(max=120) String reference, @Size(max=500) String note) {}
    public enum PaymentMethod { BANK_TRANSFER, CASH, CARD, OTHER }
    public record ReasonInput(@NotBlank @Size(max=500) String reason) {}
    public record ProfileInput(@NotBlank @Size(max=200) String name, @Size(max=255) String address,
        @Size(max=50) String phone, @Email @Size(max=100) String email, @Size(max=2000) String terms) {}
    public record ProfileView(String name,String address,String phone,String email,String terms) {}
    public record LineView(Long productSkuId,String skuCode,String description,String unit,int quantity,BigDecimal unitPrice,BigDecimal amount) {}
    public record ReceiptView(Long id,String kind,Long reversesId,BigDecimal amount,LocalDate paymentDate,
        String method,String reference,String note,Long createdBy,LocalDateTime createdAt) {}
    public record DocumentView(Long id,Long version,String number,String kind,String status,Long customerId,String customerName,
        String billingAddress,String shippingAddress,ProfileView company,LocalDate documentDate,LocalDate dueDate,
        String currency,String terms,BigDecimal total,BigDecimal received,BigDecimal outstanding,
        Long salesOrderId,String salesOrderNo,boolean orderAmountChanged,String closeReason,List<LineView> lines,List<ReceiptView> receipts) {}
}
