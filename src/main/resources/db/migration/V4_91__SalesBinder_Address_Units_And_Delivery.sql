-- Additive business simplifications; quantities and stock posting remain unchanged.
ALTER TABLE product_skus ADD COLUMN base_unit VARCHAR(20) NOT NULL DEFAULT '件';
ALTER TABLE customers ADD COLUMN shipping_address VARCHAR(255);
ALTER TABLE customers ADD COLUMN shipping_same_as_billing BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sales_order_shipments ALTER COLUMN tracking_no DROP NOT NULL;
ALTER TABLE sales_order_shipments ADD COLUMN delivery_method VARCHAR(20) NOT NULL DEFAULT 'COURIER';
ALTER TABLE sales_order_shipments ADD CONSTRAINT ck_shipment_delivery_method
    CHECK (delivery_method IN ('COURIER', 'FREIGHT', 'SELF_DELIVERY', 'PICKUP'));
