package com.wms.system.commercial;

import com.wms.system.commercial.CommercialDtos.*;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import com.wms.system.service.*;
import com.wms.system.tenant.context.CompanyScope;
import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.util.MaskingUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Commercial documents never post inventory movements. Conversion uses the existing order service. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CommercialService {
    private final CommercialDocumentRepository documents;
    private final CommercialDocumentLineRepository lines;
    private final CommercialReceiptRepository receipts;
    private final CommercialProfileRepository profiles;
    private final CustomerService customers;
    private final ProductSkuRepository skus;
    private final ProductSkuOperationalPolicy skuPolicy;
    private final SalesOrderRepository orders;
    private final SalesOrderItemRepository orderItems;
    private final SalesSubmissionService sales;
    private final TenantRepository tenants;
    private final EntityManager em;
    private final ObjectMapper json;

    public ProfileView profile() {
        return profiles.findByCompanyId(CompanyScope.currentCompanyId())
            .map(p -> new ProfileView(p.getName(),p.getAddress(),p.getPhone(),p.getEmail(),p.getTerms()))
            .orElseGet(() -> new ProfileView(tenants.findById(CompanyScope.currentCompanyId()).orElseThrow().getDisplayName(),"","","",""));
    }

    @Transactional public ProfileView saveProfile(ProfileInput input) {
        // Serialises first creation as well as updates, within this company only.
        tenants.findByIdForUpdate(CompanyScope.currentCompanyId()).orElseThrow();
        CommercialProfile p=profiles.findByCompanyId(CompanyScope.currentCompanyId()).orElseGet(CommercialProfile::new);
        p.setName(input.name().trim()); p.setAddress(input.address()); p.setPhone(input.phone()); p.setEmail(input.email()); p.setTerms(input.terms());
        profiles.saveAndFlush(p);
        return profile();
    }

    public Page<DocumentView> list(String kind,Long orderId,int page,int size) {
        if (!Set.of("QUOTE","INVOICE").contains(kind)) throw error("COMMERCIAL_INVALID_KIND");
        return documents.search(kind,customers.documentOwnerFilter(),orderId,PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size))))
            .map(d -> view(d,false));
    }

    public DocumentView get(Long id) { return view(authorized(id,false),false); }
    /** Controller requires explicit export permission because a customer-facing file contains unmasked addresses. */
    public DocumentView export(Long id) { return view(authorized(id,false),true); }

    @Transactional public DocumentView createQuote(QuoteInput input,Long actor) {
        customers.validateManualOrderCustomer(input.customerId());
        Customer c=customers.authorizedDocumentCustomer(input.customerId());
        em.lock(c,LockModeType.PESSIMISTIC_WRITE);
        String hash=hash(input);
        var existing=documents.findByKindAndRequestKey("QUOTE",input.requestKey().toString());
        if (existing.isPresent()) { requireSameHash(existing.get().getRequestHash(),hash); return view(existing.get(),false); }
        CommercialDocument d=new CommercialDocument();
        d.setKind("QUOTE"); d.setStatus("OPEN"); d.setRequestKey(input.requestKey().toString()); d.setRequestHash(hash); d.setCreatedBy(actor);
        snapshot(d,c); d.setDocumentDate(LocalDate.now());
        updateQuoteFields(d,input);
        return view(d,false);
    }

    @Transactional public DocumentView updateQuote(Long id,QuoteInput input) {
        CommercialDocument d=authorized(id,true);
        require(d,"QUOTE","OPEN");
        if (!Objects.equals(d.getVersion(),input.version())) throw error("COMMERCIAL_STALE_VERSION");
        if (!Objects.equals(d.getCustomerId(),input.customerId())) throw error("COMMERCIAL_CUSTOMER_IMMUTABLE");
        customers.validateManualOrderCustomer(d.getCustomerId());
        lines.deleteByDocumentId(id); lines.flush();
        updateQuoteFields(d,input);
        return view(d,false);
    }

    private void updateQuoteFields(CommercialDocument d,QuoteInput input) {
        if (input.validUntil().isBefore(LocalDate.now())) throw error("COMMERCIAL_QUOTE_EXPIRED");
        d.setDueDate(input.validUntil()); d.setTerms(input.terms());
        if (input.shippingAddress()!=null && !input.shippingAddress().isBlank()) d.setShippingAddress(input.shippingAddress().trim());
        List<CommercialDocumentLine> pending=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        for (LineInput item:input.lines()) {
            ProductSku sku=skus.findById(item.productSkuId()).orElseThrow(() -> error("PRODUCT_SKU_NOT_FOUND"));
            skuPolicy.requireEnabled(sku,ProductSkuOperationalPolicy.SALES_ORDER);
            CommercialDocumentLine l=line(sku,item.quantity(),item.unitPrice(),pending.size());
            pending.add(l); total=total.add(l.getAmount());
        }
        d.setTotal(money(total)); documents.saveAndFlush(d);
        for (var l:pending) l.setDocumentId(d.getId());
        lines.saveAllAndFlush(pending);
    }

    @Transactional public DocumentView convert(Long id,Long actor,String username) {
        CommercialDocument d=authorized(id,true);
        if ("QUOTE".equals(d.getKind()) && "CONVERTED".equals(d.getStatus())) return view(d,false);
        require(d,"QUOTE","OPEN");
        if (d.getDueDate().isBefore(LocalDate.now())) throw error("COMMERCIAL_QUOTE_EXPIRED");
        Customer c=customers.authorizedDocumentCustomer(d.getCustomerId());
        CreateSalesOrderRequest input=CreateSalesOrderRequest.builder().customerId(d.getCustomerId()).saveAsDraft(false)
            .shipAddress1(d.getShippingAddress()).consigneeName(c.getContact()).consigneePhone(c.getPhone())
            .items(lines.findByDocumentIdOrderByPosition(id).stream().map(l -> CreateSalesOrderRequest.SalesOrderItemData.builder()
                .productSkuId(l.getProductSkuId()).quantity(l.getQuantity()).unitPrice(l.getUnitPrice()).build()).toList()).build();
        var order=sales.createSalesOrder(input,actor,username);
        d.setSalesOrderId(order.getId()); d.setStatus("CONVERTED"); documents.saveAndFlush(d);
        return view(d,false);
    }

    @Transactional public DocumentView cancelQuote(Long id,String reason) {
        CommercialDocument d=authorized(id,true);
        if ("QUOTE".equals(d.getKind()) && "CANCELLED".equals(d.getStatus())) return view(d,false);
        require(d,"QUOTE","OPEN"); d.setStatus("CANCELLED"); d.setCloseReason(reason); documents.saveAndFlush(d);
        return view(d,false);
    }

    @Transactional public DocumentView createInvoice(InvoiceInput input,Long actor) {
        SalesOrder order=orders.findByIdForUpdate(input.salesOrderId()).orElseThrow(() -> error("SALES_ORDER_NOT_FOUND"));
        Customer c=customers.authorizedDocumentCustomer(order.getCustomerId());
        String hash=hash(input);
        var retry=documents.findByKindAndRequestKey("INVOICE",input.requestKey().toString());
        if (retry.isPresent()) { requireSameHash(retry.get().getRequestHash(),hash); return view(retry.get(),false); }
        var existing=documents.findByKindAndSalesOrderIdAndStatus("INVOICE",order.getId(),"ISSUED");
        if (existing.isPresent()) return view(existing.get(),false);
        if (!Set.of(CommercialStatus.APPROVED,CommercialStatus.COMPLETED,CommercialStatus.CLOSED).contains(order.getCommercialStatus())
                || order.getOrderPurpose()!=SalesOrderPurpose.NORMAL) throw error("COMMERCIAL_ORDER_NOT_CONFIRMED");
        CommercialDocument d=new CommercialDocument(); snapshot(d,c);
        d.setKind("INVOICE"); d.setStatus("ISSUED"); d.setSalesOrderId(order.getId()); d.setCreatedBy(actor);
        d.setRequestKey(input.requestKey().toString()); d.setRequestHash(hash); d.setDocumentDate(LocalDate.now()); d.setDueDate(input.dueDate());
        if (order.getShipAddress1()!=null) d.setShippingAddress(order.getShipAddress1());
        List<CommercialDocumentLine> pending=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for (SalesOrderItem item:orderItems.findBySalesOrderId(order.getId())) {
            int qty=effectiveQuantity(item);
            if (qty<=0) continue;
            ProductSku sku=skus.findById(item.getProductSkuId()).orElseThrow(() -> error("PRODUCT_SKU_NOT_FOUND"));
            var l=line(sku,qty,item.getUnitPrice(),pending.size()); pending.add(l); total=total.add(l.getAmount());
        }
        if (pending.isEmpty()) throw error("COMMERCIAL_EMPTY_INVOICE");
        d.setTotal(money(total)); documents.saveAndFlush(d);
        for (var l:pending) l.setDocumentId(d.getId()); lines.saveAllAndFlush(pending);
        return view(d,false);
    }

    @Transactional public DocumentView recordReceipt(Long id,ReceiptInput input,Long actor) {
        CommercialDocument d=authorized(id,true);
        String hash=hash(Arrays.asList(id,input));
        var retry=receipts.findByRequestKey(input.requestKey().toString());
        if (retry.isPresent()) { requireSameHash(retry.get().getRequestHash(),hash); return view(d,false); }
        require(d,"INVOICE","ISSUED");
        BigDecimal amount=money(input.amount());
        if (amount.signum()<=0 || amount.compareTo(d.getTotal().subtract(receipts.paid(id)))>0) throw error("COMMERCIAL_OVERPAYMENT");
        if (input.paymentDate().isAfter(LocalDate.now())) throw error("COMMERCIAL_FUTURE_PAYMENT");
        CommercialReceipt r=new CommercialReceipt(); r.setDocumentId(id); r.setKind("RECEIPT"); r.setAmount(amount);
        r.setRequestKey(input.requestKey().toString()); r.setRequestHash(hash); r.setCreatedBy(actor); r.setPaymentDate(input.paymentDate());
        r.setMethod(input.method().name()); r.setReference(input.reference()); r.setNote(input.note()); receipts.saveAndFlush(r);
        return view(d,false);
    }

    /** A mistaken entry is reversed by a new evidence row; it is never edited or deleted. This does not send a refund. */
    @Transactional public DocumentView reverseReceipt(Long id,Long receiptId,String reason,Long actor) {
        CommercialDocument d=authorized(id,true); require(d,"INVOICE","ISSUED");
        CommercialReceipt original=receipts.findById(receiptId).filter(r -> r.getDocumentId().equals(id) && "RECEIPT".equals(r.getKind()))
            .orElseThrow(() -> error("COMMERCIAL_RECEIPT_NOT_FOUND"));
        if (receipts.findByReversesId(receiptId).isPresent()) return view(d,false);
        CommercialReceipt r=new CommercialReceipt(); r.setDocumentId(id); r.setKind("REVERSAL"); r.setReversesId(receiptId);
        r.setAmount(original.getAmount()); r.setPaymentDate(LocalDate.now()); r.setMethod(original.getMethod()); r.setNote(reason); r.setCreatedBy(actor);
        r.setRequestKey(UUID.randomUUID().toString()); r.setRequestHash(hash(Arrays.asList(id,receiptId,reason))); receipts.saveAndFlush(r);
        return view(d,false);
    }

    @Transactional public DocumentView voidInvoice(Long id,String reason) {
        CommercialDocument d=authorized(id,true);
        if ("INVOICE".equals(d.getKind()) && "VOID".equals(d.getStatus())) return view(d,false);
        require(d,"INVOICE","ISSUED");
        if (receipts.paid(id).signum()!=0) throw error("COMMERCIAL_PAID_INVOICE_VOID");
        d.setStatus("VOID"); d.setCloseReason(reason); documents.saveAndFlush(d); return view(d,false);
    }

    private CommercialDocument authorized(Long id,boolean lock) {
        CommercialDocument d=(lock ? documents.lock(id) : documents.findById(id)).orElseThrow(() -> error("COMMERCIAL_DOCUMENT_NOT_FOUND"));
        customers.authorizedDocumentCustomer(d.getCustomerId()); return d;
    }
    private DocumentView view(CommercialDocument d,boolean forExport) {
        boolean masked=!forExport && customers.maskDocumentCustomer();
        var items=lines.findByDocumentIdOrderByPosition(d.getId()).stream().map(l -> new LineView(l.getProductSkuId(),l.getSkuCode(),l.getDescription(),l.getUnit(),l.getQuantity(),l.getUnitPrice(),l.getAmount())).toList();
        var payments=receipts.findByDocumentIdOrderById(d.getId()).stream().map(r -> new ReceiptView(r.getId(),r.getKind(),r.getReversesId(),r.getAmount(),r.getPaymentDate(),r.getMethod(),r.getReference(),r.getNote(),r.getCreatedBy(),r.getCreatedAt())).toList();
        BigDecimal received=payments.stream().map(r -> "RECEIPT".equals(r.kind()) ? r.amount() : r.amount().negate()).reduce(BigDecimal.ZERO,BigDecimal::add);
        SalesOrder order=d.getSalesOrderId()==null ? null : orders.findById(d.getSalesOrderId()).orElse(null);
        boolean changed="INVOICE".equals(d.getKind()) && order!=null && currentOrderAmount(order.getId()).compareTo(d.getTotal())!=0;
        return new DocumentView(d.getId(),d.getVersion(),number(d),d.getKind(),d.getStatus(),d.getCustomerId(),
            masked ? MaskingUtils.maskName(d.getCustomerName()) : d.getCustomerName(),
            masked ? MaskingUtils.maskAddress(d.getBillingAddress()) : d.getBillingAddress(),masked ? MaskingUtils.maskAddress(d.getShippingAddress()) : d.getShippingAddress(),
            new ProfileView(d.getCompanyName(),d.getCompanyAddress(),d.getCompanyPhone(),d.getCompanyEmail(),d.getTerms()),
            d.getDocumentDate(),d.getDueDate(),"GBP",d.getTerms(),d.getTotal(),received,
            "INVOICE".equals(d.getKind()) && "ISSUED".equals(d.getStatus()) ? d.getTotal().subtract(received) : BigDecimal.ZERO,
            d.getSalesOrderId(),order==null?null:order.getOrderNo(),changed,d.getCloseReason(),items,payments);
    }
    private BigDecimal currentOrderAmount(Long orderId) {
        return orderItems.findBySalesOrderId(orderId).stream().map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(effectiveQuantity(i)))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    static int effectiveQuantity(SalesOrderItem i) {
        return Math.max(0,(i.getRequestedQty()!=null && i.getRequestedQty()>0 ? i.getRequestedQty() : i.getQuantity())-Optional.ofNullable(i.getCancelledQty()).orElse(0));
    }
    private void snapshot(CommercialDocument d,Customer c) {
        ProfileView p=profile(); d.setCustomerId(c.getId()); d.setCustomerName(c.getName()); d.setBillingAddress(c.getAddress());
        d.setShippingAddress(Boolean.FALSE.equals(c.getShippingSameAsBilling())?c.getShippingAddress():c.getAddress());
        d.setCompanyName(p.name()); d.setCompanyAddress(p.address()); d.setCompanyPhone(p.phone()); d.setCompanyEmail(p.email()); d.setTerms(p.terms());
    }
    private static CommercialDocumentLine line(ProductSku s,int quantity,BigDecimal price,int position) {
        if (quantity<1 || quantity>1000000 || price==null || price.signum()<0 || price.precision()-price.scale()>8 || price.stripTrailingZeros().scale()>2) throw error("COMMERCIAL_INVALID_AMOUNT");
        var l=new CommercialDocumentLine(); l.setProductSkuId(s.getId()); l.setSkuCode(s.getSkuCode()); l.setDescription(s.getName()); l.setUnit(s.getDisplayBaseUnit());
        l.setPosition(position); l.setQuantity(quantity); l.setUnitPrice(money(price)); l.setAmount(money(price.multiply(BigDecimal.valueOf(quantity)))); return l;
    }
    static BigDecimal money(BigDecimal amount) {
        if (amount==null || amount.abs().compareTo(new BigDecimal("9999999999999.99"))>0) throw error("COMMERCIAL_INVALID_AMOUNT");
        try { return amount.setScale(2,RoundingMode.UNNECESSARY); } catch(ArithmeticException ex) { throw error("COMMERCIAL_INVALID_AMOUNT"); }
    }
    public static String number(CommercialDocument d) { return ("QUOTE".equals(d.getKind())?"QT-":"INV-")+String.format("%06d",d.getId()); }
    private String hash(Object input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input))); }
        catch(Exception ex) { throw new IllegalStateException("Cannot fingerprint commercial request",ex); }
    }
    private static void requireSameHash(String oldHash,String newHash) { if(!Objects.equals(oldHash,newHash)) throw error("COMMERCIAL_REQUEST_CONFLICT"); }
    private static void require(CommercialDocument d,String kind,String status) { if(!kind.equals(d.getKind()) || !status.equals(d.getStatus())) throw error("COMMERCIAL_INVALID_STATE"); }
    private static BusinessException error(String key) { return new BusinessException(key); }
}
