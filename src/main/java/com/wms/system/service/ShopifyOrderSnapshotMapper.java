package com.wms.system.service;

import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.shopify.ShopifyAddressDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class ShopifyOrderSnapshotMapper {

    private ShopifyOrderSnapshotMapper() {
    }

    static void applyShippingSnapshot(ShopifyOrderDto order, CreateSalesOrderRequest request) {
        ShopifyAddressDto address = order == null ? null : order.getShippingAddress();
        if (address == null) {
            return;
        }

        String consignee = StringUtils.hasText(address.getName())
            ? address.getName()
            : joinName(address.getFirstName(), address.getLastName());
        request.setConsigneeName(trimToLength(consignee, 200));
        request.setConsigneePhone(trimToLength(address.getPhone(), 50));
        request.setShipAddress1(trimToLength(address.getAddress1(), 255));
        request.setShipAddress2(trimToLength(address.getAddress2(), 255));
        request.setShipCity(trimToLength(address.getCity(), 100));
        request.setShipProvince(trimToLength(address.getProvince(), 100));
        request.setShipZip(trimToLength(address.getZip(), 30));

        String countryCode = trimToLength(address.getCountryCode(), 10);
        request.setShipCountryCode(countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT));
    }

    private static String joinName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        String value = (first + " " + last).trim();
        return value.isEmpty() ? null : value;
    }

    private static String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
