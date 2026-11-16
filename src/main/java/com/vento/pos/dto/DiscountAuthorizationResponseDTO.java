package com.vento.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DiscountAuthorizationResponseDTO(String token, BigDecimal amount, LocalDateTime expiresAt) {
}
