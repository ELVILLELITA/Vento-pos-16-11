package com.vento.pos.dto;

import com.vento.pos.entity.Promotion;
import com.vento.pos.entity.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionResponseDTO(
        Long id,
        String name,
        PromotionType type,
        boolean active,
        int priority,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long productId,
        String category,
        Integer buyQuantity,
        Integer freeQuantity,
        BigDecimal discountPercent,
        BigDecimal minimumSpend
) {
    public static PromotionResponseDTO from(Promotion promotion) {
        return new PromotionResponseDTO(
                promotion.getId(), promotion.getName(), promotion.getType(), promotion.isActive(),
                promotion.getPriority(), promotion.getStartsAt(), promotion.getEndsAt(), promotion.getProductId(),
                promotion.getCategory(), promotion.getBuyQuantity(), promotion.getFreeQuantity(),
                promotion.getDiscountPercent(), promotion.getMinimumSpend());
    }
}
