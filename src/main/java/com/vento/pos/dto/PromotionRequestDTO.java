package com.vento.pos.dto;

import com.vento.pos.entity.PromotionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionRequestDTO(
        @NotBlank @Size(max = 120) String name,
        @NotNull PromotionType type,
        @NotNull Boolean active,
        @NotNull @Min(0) Integer priority,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Positive Long productId,
        @Size(max = 60) String category,
        @Min(1) Integer buyQuantity,
        @Min(1) Integer freeQuantity,
        @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal minimumSpend
) {
}
