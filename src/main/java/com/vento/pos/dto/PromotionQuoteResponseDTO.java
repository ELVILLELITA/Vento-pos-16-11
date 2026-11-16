package com.vento.pos.dto;

import java.math.BigDecimal;

public record PromotionQuoteResponseDTO(BigDecimal discount, String promotionName) {
}
