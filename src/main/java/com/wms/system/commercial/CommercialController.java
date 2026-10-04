package com.wms.system.commercial;
import com.wms.system.commercial.CommercialDtos.*;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/commercial") @RequiredArgsConstructor
public class CommercialController {
    private final CommercialService service;
    private final CommercialExportService exports;

    @GetMapping("/profile") @PreAuthorize("hasAnyAuthority('quotation:view','invoice:view','TENANT_ADMIN')")
    public ProfileView profile() { return service.profile(); }
    @PutMapping("/profile") @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ProfileView profile(@RequestBody @Valid ProfileInput input) { return service.saveProfile(input); }
    @GetMapping("/quotes") @PreAuthorize("hasAnyAuthority('quotation:view','TENANT_ADMIN')")
    public Page<DocumentView> quotes(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list("QUOTE",null,page,size); }
    @GetMapping("/invoices") @PreAuthorize("hasAnyAuthority('invoice:view','TENANT_ADMIN')")
    public Page<DocumentView> invoices(@RequestParam(required=false) Long salesOrderId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list("INVOICE",salesOrderId,page,size); }
    @GetMapping("/quotes/{id}") @PreAuthorize("hasAnyAuthority('quotation:view','TENANT_ADMIN')")
    public DocumentView quote(@PathVariable Long id) { return kind(service.get(id),"QUOTE"); }
    @GetMapping("/invoices/{id}") @PreAuthorize("hasAnyAuthority('invoice:view','TENANT_ADMIN')")
    public DocumentView invoice(@PathVariable Long id) { return kind(service.get(id),"INVOICE"); }
    @PostMapping("/quotes") @PreAuthorize("hasAnyAuthority('quotation:edit','TENANT_ADMIN')")
    public DocumentView createQuote(@RequestBody @Valid QuoteInput input,Authentication auth) { return service.createQuote(input,AuthUserResolver.resolveUserId(auth)); }
    @PutMapping("/quotes/{id}") @PreAuthorize("hasAnyAuthority('quotation:edit','TENANT_ADMIN')")
    public DocumentView updateQuote(@PathVariable Long id,@RequestBody @Valid QuoteInput input) { return service.updateQuote(id,input); }
    @PostMapping("/quotes/{id}/convert")
    @PreAuthorize("hasAuthority('TENANT_ADMIN') or (hasAuthority('quotation:convert') and hasAuthority('sales:create'))")
    public DocumentView convert(@PathVariable Long id,Authentication auth) { return service.convert(id,AuthUserResolver.resolveUserId(auth),AuthUserResolver.resolveUsername(auth)); }
    @PostMapping("/quotes/{id}/cancel") @PreAuthorize("hasAnyAuthority('quotation:edit','TENANT_ADMIN')")
    public DocumentView cancel(@PathVariable Long id,@RequestBody @Valid ReasonInput input) { return service.cancelQuote(id,input.reason()); }
    @PostMapping("/invoices") @PreAuthorize("hasAnyAuthority('invoice:create','TENANT_ADMIN')")
    public DocumentView invoice(@RequestBody @Valid InvoiceInput input,Authentication auth) { return service.createInvoice(input,AuthUserResolver.resolveUserId(auth)); }
    @PostMapping("/invoices/{id}/receipts") @PreAuthorize("hasAnyAuthority('invoice:receive','TENANT_ADMIN')")
    public DocumentView receive(@PathVariable Long id,@RequestBody @Valid ReceiptInput input,Authentication auth) { return service.recordReceipt(id,input,AuthUserResolver.resolveUserId(auth)); }
    @PostMapping("/invoices/{id}/receipts/{receiptId}/reverse") @PreAuthorize("hasAnyAuthority('invoice:receive','TENANT_ADMIN')")
    public DocumentView reverse(@PathVariable Long id,@PathVariable Long receiptId,@RequestBody @Valid ReasonInput input,Authentication auth) { return service.reverseReceipt(id,receiptId,input.reason(),AuthUserResolver.resolveUserId(auth)); }
    @PostMapping("/invoices/{id}/void") @PreAuthorize("hasAnyAuthority('invoice:create','TENANT_ADMIN')")
    public DocumentView voidInvoice(@PathVariable Long id,@RequestBody @Valid ReasonInput input) { return service.voidInvoice(id,input.reason()); }
    @GetMapping("/quotes/{id}/pdf") @PreAuthorize("hasAuthority('TENANT_ADMIN') or (hasAuthority('quotation:view') and hasAuthority('commercial:export'))")
    public ResponseEntity<byte[]> quotePdf(@PathVariable Long id) { var d=kind(service.export(id),"QUOTE"); return file(d.number()+".pdf","application/pdf",exports.pdf(d)); }
    @GetMapping("/quotes/{id}/xlsx") @PreAuthorize("hasAuthority('TENANT_ADMIN') or (hasAuthority('quotation:view') and hasAuthority('commercial:export'))")
    public ResponseEntity<byte[]> quoteXlsx(@PathVariable Long id) { var d=kind(service.export(id),"QUOTE"); return file(d.number()+".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",exports.xlsx(d)); }
    @GetMapping("/invoices/{id}/pdf") @PreAuthorize("hasAuthority('TENANT_ADMIN') or (hasAuthority('invoice:view') and hasAuthority('commercial:export'))")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) { var d=kind(service.export(id),"INVOICE"); return file(d.number()+".pdf","application/pdf",exports.pdf(d)); }
    private static DocumentView kind(DocumentView d,String expected) { if(!expected.equals(d.kind())) throw new BusinessException("COMMERCIAL_DOCUMENT_NOT_FOUND"); return d; }
    private static ResponseEntity<byte[]> file(String name,String type,byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString()).body(bytes);
    }
}
