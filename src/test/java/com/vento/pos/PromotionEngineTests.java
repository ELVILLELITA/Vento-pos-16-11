package com.vento.pos;

import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Promotion;
import com.vento.pos.entity.PromotionType;
import com.vento.pos.service.PromotionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromotionEngineTests {
    private final PromotionEngine engine = new PromotionEngine();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Test
    void buyXGetYDiscountsOnlyFreeUnitsAndUsesUnitRounding() {
        Producto coffee = product(1L, "Café", "10.00");
        Promotion promotion = new Promotion("2x1", PromotionType.BUY_X_GET_Y);
        promotion.setActive(true);
        promotion.setProductId(1L);
        promotion.setBuyQuantity(1);
        promotion.setFreeQuantity(1);

        var result = engine.calculate(List.of(promotion),
                List.of(new PromotionEngine.Item(coffee, 3, new BigDecimal("10.00"))), now);

        assertEquals(new BigDecimal("10.00"), result.discount());
        assertEquals(new BigDecimal("10.00"), result.discountsByProduct().get(1L));
    }

    @Test
    void percentProductAndCategoryMatchOnlyTheirEligibleLines() {
        Producto coffee = product(1L, "Café", "10.04");
        Producto tea = product(2L, "Té", "20.00");
        tea.setCategoria("Bebidas");
        Promotion promotion = new Promotion("Café 12.5%", PromotionType.CATEGORY_PERCENT);
        promotion.setActive(true);
        promotion.setCategory("café");
        promotion.setDiscountPercent(new BigDecimal("12.50"));

        var result = engine.calculate(List.of(promotion), List.of(
                new PromotionEngine.Item(coffee, 1, new BigDecimal("10.04")),
                new PromotionEngine.Item(tea, 1, new BigDecimal("20.00"))), now);

        assertEquals(new BigDecimal("1.26"), result.discount());
        assertEquals(new BigDecimal("1.26"), result.discountsByProduct().get(1L));
        assertEquals(null, result.discountsByProduct().get(2L));
    }

    @Test
    void spendTiersUseTheHighestEligibleNonStackingOffer() {
        Producto item = product(3L, "Taza", "100.00");
        Promotion lowTier = tier("10% desde 50", "50.00", "10");
        lowTier.setPriority(1);
        Promotion highTier = tier("15% desde 100", "100.00", "15");
        highTier.setPriority(2);
        Promotion futureTier = tier("Futuro", "1.00", "100");
        futureTier.setStartsAt(now.plusDays(1));

        var result = engine.calculate(List.of(lowTier, highTier, futureTier),
                List.of(new PromotionEngine.Item(item, 1, new BigDecimal("100.00"))), now);

        assertEquals(new BigDecimal("15.00"), result.discount());
        assertEquals("15% desde 100", result.promotionName());
    }

    private Promotion tier(String name, String minimum, String percent) {
        Promotion promotion = new Promotion(name, PromotionType.SPEND_TIER);
        promotion.setActive(true);
        promotion.setMinimumSpend(new BigDecimal(minimum));
        promotion.setDiscountPercent(new BigDecimal(percent));
        return promotion;
    }

    private Producto product(long id, String name, String price) {
        Producto product = new Producto(name, new BigDecimal(price), "Café", true, 10);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
