package com.vento.pos.service;

import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Promotion;
import com.vento.pos.entity.PromotionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PromotionEngine {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public record Item(Producto product, int quantity, BigDecimal unitBase) { }
    public record Result(BigDecimal discount, String promotionName, Map<Long, BigDecimal> discountsByProduct) { }

    public Result calculate(List<Promotion> promotions, List<Item> items, LocalDateTime at) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.unitBase().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(ZERO, BigDecimal::add).setScale(2, ROUNDING);
        Promotion selected = null;
        BigDecimal bestDiscount = ZERO;
        Map<Long, BigDecimal> selectedAllocations = Map.of();
        for (Promotion promotion : promotions) {
            if (!eligibleNow(promotion, at)) continue;
            Map<Long, BigDecimal> allocations = discountsFor(promotion, items, subtotal);
            BigDecimal candidate = allocations.values().stream().reduce(ZERO, BigDecimal::add)
                    .setScale(2, ROUNDING);
            if (candidate.compareTo(bestDiscount) > 0
                    || (candidate.compareTo(bestDiscount) == 0 && candidate.signum() > 0
                    && (selected == null || promotion.getPriority() > selected.getPriority()
                    || (promotion.getPriority() == selected.getPriority()
                    && promotion.getId() != null && selected.getId() != null
                    && promotion.getId() < selected.getId())))) {
                selected = promotion;
                bestDiscount = candidate;
                selectedAllocations = allocations;
            }
        }
        return new Result(bestDiscount.min(subtotal).setScale(2, ROUNDING),
                selected == null ? null : selected.getName(), selectedAllocations);
    }

    private Map<Long, BigDecimal> discountsFor(Promotion promotion, List<Item> items, BigDecimal subtotal) {
        List<Item> matching = items.stream().filter(item -> matches(promotion, item.product())).toList();
        return switch (promotion.getType()) {
            case BUY_X_GET_Y -> {
                if (promotion.getBuyQuantity() == null || promotion.getFreeQuantity() == null
                        || promotion.getBuyQuantity() <= 0 || promotion.getFreeQuantity() <= 0) yield Map.of();
                Map<Long, BigDecimal> result = new LinkedHashMap<>();
                matching.forEach(item -> {
                    int freeUnits = (int) (item.quantity() / ((long) promotion.getBuyQuantity() + promotion.getFreeQuantity())
                            * promotion.getFreeQuantity());
                    if (freeUnits > 0) result.put(item.product().getId(),
                            item.unitBase().multiply(BigDecimal.valueOf(freeUnits)).setScale(2, ROUNDING));
                });
                yield result;
            }
            case PRODUCT_PERCENT, CATEGORY_PERCENT -> {
                Map<Long, BigDecimal> result = new LinkedHashMap<>();
                matching.forEach(item -> {
                    BigDecimal discount = percentDiscount(
                            item.unitBase().multiply(BigDecimal.valueOf(item.quantity())), promotion.getDiscountPercent());
                    if (discount.signum() > 0) result.put(item.product().getId(), discount);
                });
                yield result;
            }
            case SPEND_TIER -> promotion.getMinimumSpend() != null
                    && subtotal.compareTo(promotion.getMinimumSpend()) >= 0
                    ? allocatePercent(items, percentDiscount(subtotal, promotion.getDiscountPercent())) : Map.of();
        };
    }

    private Map<Long, BigDecimal> allocatePercent(List<Item> items, BigDecimal amount) {
        BigDecimal baseTotal = items.stream()
                .map(item -> item.unitBase().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(ZERO, BigDecimal::add);
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal allocated = ZERO;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            BigDecimal lineBase = item.unitBase().multiply(BigDecimal.valueOf(item.quantity()));
            BigDecimal share = i == items.size() - 1
                    ? amount.subtract(allocated)
                    : amount.multiply(lineBase).divide(baseTotal, 2, RoundingMode.DOWN);
            share = share.min(lineBase);
            if (share.signum() > 0) result.put(item.product().getId(), share);
            allocated = allocated.add(share);
        }
        return result;
    }

    private boolean matches(Promotion promotion, Producto product) {
        return switch (promotion.getType()) {
            case BUY_X_GET_Y, PRODUCT_PERCENT -> promotion.getProductId() != null
                    && promotion.getProductId().equals(product.getId());
            case CATEGORY_PERCENT -> promotion.getCategory() != null
                    && promotion.getCategory().equalsIgnoreCase(product.getCategoria());
            case SPEND_TIER -> true;
        };
    }

    private BigDecimal percentDiscount(BigDecimal base, BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) return ZERO;
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, ROUNDING);
    }

    private boolean eligibleNow(Promotion promotion, LocalDateTime at) {
        return promotion.isActive()
                && (promotion.getStartsAt() == null || !at.isBefore(promotion.getStartsAt()))
                && (promotion.getEndsAt() == null || !at.isAfter(promotion.getEndsAt()));
    }
}
