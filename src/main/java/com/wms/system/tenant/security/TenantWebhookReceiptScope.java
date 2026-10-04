package com.wms.system.tenant.security;

/** Links every diagnostic payload copy made by the synchronous worker, including REQUIRES_NEW. */
public final class TenantWebhookReceiptScope implements AutoCloseable {
    private static final ThreadLocal<Long> CURRENT=new ThreadLocal<>();
    private final Long previous;
    private TenantWebhookReceiptScope(long id) { previous=CURRENT.get(); CURRENT.set(id); }
    public static TenantWebhookReceiptScope open(long id) { return new TenantWebhookReceiptScope(id); }
    public static Long currentId() { return CURRENT.get(); }
    @Override public void close() { if(previous==null) CURRENT.remove(); else CURRENT.set(previous); }
}
