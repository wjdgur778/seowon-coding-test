package com.seowon.coding.domain.dto;

import lombok.Data;

import java.util.List;


@Data
public class OrderRequestDto {
    String customerName;
    String customerEmail;
    List<ProductDto> products;
}
