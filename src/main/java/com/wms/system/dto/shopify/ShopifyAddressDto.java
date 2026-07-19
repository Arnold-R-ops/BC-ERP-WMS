package com.wms.system.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyAddressDto {

    private String name;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String phone;
    private String address1;
    private String address2;
    private String city;
    private String province;
    private String zip;

    @JsonProperty("country_code")
    private String countryCode;
}
