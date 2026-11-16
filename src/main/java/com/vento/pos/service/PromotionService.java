package com.vento.pos.service;

import com.vento.pos.dto.PromotionRequestDTO;
import com.vento.pos.dto.PromotionResponseDTO;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Promotion;
import com.vento.pos.entity.PromotionType;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.PromotionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PromotionService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PromotionRepository promotionRepository;
    private final ProductoRepository productoRepository;
    private final ConfiguracionSistemaService configuracionService;
    private final PromotionEngine engine = new PromotionEngine();

    public PromotionService(PromotionRepository promotionRepository, ProductoRepository productoRepository,
                           ConfiguracionSistemaService configuracionService) {
        this.promotionRepository = promotionRepository;
        this.productoRepository = productoRepository;
        this.configuracionService = configuracionService;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDTO> list(boolean activeOnly) {
        return (activeOnly ? promotionRepository.findAllByActiveTrueOrderByPriorityDescIdAsc()
                : promotionRepository.findAllByOrderByPriorityDescIdAsc()).stream()
                .map(PromotionResponseDTO::from).toList();
    }

    @Transactional
    public PromotionResponseDTO create(PromotionRequestDTO request) {
        validate(request);
        Promotion promotion = new Promotion(request.name().trim(), request.type());
        update(promotion, request);
        return PromotionResponseDTO.from(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponseDTO update(Long id, PromotionRequestDTO request) {
        validate(request);
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la promoción."));
        update(promotion, request);
        return PromotionResponseDTO.from(promotionRepository.save(promotion));
    }

    @Transactional
    public void delete(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la promoción.");
        }
        promotionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PromotionEngine.Result quote(List<com.vento.pos.dto.DetalleVentaDTO> requestItems) {
        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            requestItems.forEach(item -> quantities.merge(item.productoId(), item.cantidad(), Math::addExact));
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada es demasiado grande.");
        }
        List<PromotionEngine.Item> items = quantities.entrySet().stream().map(entry -> {
            Producto product = productoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el producto."));
            return new PromotionEngine.Item(product, entry.getValue(), unitBase(product));
        }).toList();
        return engine.calculate(promotionRepository.findAllByActiveTrueOrderByPriorityDescIdAsc(), items, LocalDateTime.now());
    }

    public BigDecimal unitBase(Producto product) {
        BigDecimal rate = configuracionService.obtenerEntidad().getPorcentajeIva()
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return product.isPrecioIncluyeIva()
                ? product.getPrecio().divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP)
                : product.getPrecio().setScale(2, RoundingMode.HALF_UP);
    }

    public PromotionEngine.Result calculate(List<PromotionEngine.Item> items, LocalDateTime at) {
        return engine.calculate(promotionRepository.findAllByActiveTrueOrderByPriorityDescIdAsc(), items, at);
    }

    private void update(Promotion promotion, PromotionRequestDTO request) {
        promotion.setName(request.name().trim());
        promotion.setType(request.type());
        promotion.setActive(Boolean.TRUE.equals(request.active()));
        promotion.setPriority(request.priority());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        boolean productRule = request.type() == PromotionType.BUY_X_GET_Y
                || request.type() == PromotionType.PRODUCT_PERCENT;
        promotion.setProductId(productRule ? request.productId() : null);
        promotion.setCategory(request.type() == PromotionType.CATEGORY_PERCENT
                ? (request.category() == null ? null : request.category().trim()) : null);
        promotion.setBuyQuantity(request.type() == PromotionType.BUY_X_GET_Y ? request.buyQuantity() : null);
        promotion.setFreeQuantity(request.type() == PromotionType.BUY_X_GET_Y ? request.freeQuantity() : null);
        promotion.setDiscountPercent(request.type() == PromotionType.BUY_X_GET_Y ? null : request.discountPercent());
        promotion.setMinimumSpend(request.type() == PromotionType.SPEND_TIER ? request.minimumSpend() : null);
    }

    private void validate(PromotionRequestDTO request) {
        if (request.startsAt() != null && request.endsAt() != null && request.startsAt().isAfter(request.endsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior a la fecha final.");
        }
        PromotionType type = request.type();
        if ((type == PromotionType.BUY_X_GET_Y || type == PromotionType.PRODUCT_PERCENT)
                && request.productId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un producto para esta promoción.");
        }
        if (request.productId() != null) {
            Producto product = productoRepository.findById(request.productId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "El producto seleccionado no existe."));
            if (!Boolean.TRUE.equals(product.getActivo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto seleccionado está inactivo.");
            }
        }
        if (type == PromotionType.CATEGORY_PERCENT && (request.category() == null || request.category().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una categoría para esta promoción.");
        }
        if (type == PromotionType.CATEGORY_PERCENT && productoRepository.findActiveCategories().stream()
                .noneMatch(category -> category.equalsIgnoreCase(request.category().trim()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría seleccionada no está activa.");
        }
        if (type == PromotionType.BUY_X_GET_Y
                && (request.buyQuantity() == null || request.freeQuantity() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las cantidades de compra y unidades gratis.");
        }
        if ((type == PromotionType.PRODUCT_PERCENT || type == PromotionType.CATEGORY_PERCENT
                || type == PromotionType.SPEND_TIER) && request.discountPercent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el porcentaje de descuento.");
        }
        if (type == PromotionType.SPEND_TIER
                && (request.minimumSpend() == null || request.minimumSpend().compareTo(ZERO) <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el gasto mínimo de la promoción.");
        }
    }
}
