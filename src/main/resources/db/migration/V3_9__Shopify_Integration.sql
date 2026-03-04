-- V3.9 Shopify Integration
-- Create integration_configs table and modify sales_orders table

-- Create integration_configs table
CREATE TABLE integration_configs (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL DEFAULT 'SHOPIFY',
    store_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    access_token VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for integration_configs
CREATE INDEX idx_integration_platform_active ON integration_configs(platform, is_active);

-- Modify sales_orders table to add channel and external order tracking
ALTER TABLE sales_orders
ADD COLUMN channel VARCHAR(50) DEFAULT 'MANUAL',
ADD COLUMN external_order_id VARCHAR(100),
ADD COLUMN external_order_no VARCHAR(100);

-- Create indexes for sales_orders new columns
CREATE INDEX idx_sales_external_order ON sales_orders(external_order_id);
CREATE INDEX idx_sales_channel ON sales_orders(channel);
