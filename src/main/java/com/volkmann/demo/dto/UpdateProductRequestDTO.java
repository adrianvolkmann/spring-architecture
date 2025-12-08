package com.volkmann.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO for updating an existing Product.
 * Used in: PUT /api/products/{id}
 *
 * As per ADR-006: DTOs Separados por Operação
 *
 * Note: Fields like 'id', 'createdAt', 'updatedAt' are NOT included as they are immutable
 * or managed by the system.
 */
public record UpdateProductRequestDTO(

        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
        BigDecimal price,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity must be greater than or equal to zero")
        Integer stockQuantity,

        Boolean active
) {
}

