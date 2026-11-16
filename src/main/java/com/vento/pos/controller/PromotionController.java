package com.vento.pos.controller;

import com.vento.pos.dto.PromotionQuoteRequestDTO;
import com.vento.pos.dto.PromotionQuoteResponseDTO;
import com.vento.pos.dto.PromotionRequestDTO;
import com.vento.pos.dto.PromotionResponseDTO;
import com.vento.pos.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/promotions", "/api/v1/promociones"})
public class PromotionController {
    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<PromotionResponseDTO> list() {
        return promotionService.list(false);
    }

    @GetMapping("/active")
    public List<PromotionResponseDTO> active() {
        return promotionService.list(true);
    }

    @PostMapping("/quote")
    public PromotionQuoteResponseDTO quote(@Valid @RequestBody PromotionQuoteRequestDTO request) {
        var result = promotionService.quote(request.items());
        return new PromotionQuoteResponseDTO(result.discount(), result.promotionName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponseDTO create(@Valid @RequestBody PromotionRequestDTO request) {
        return promotionService.create(request);
    }

    @PutMapping("/{id}")
    public PromotionResponseDTO update(@PathVariable Long id, @Valid @RequestBody PromotionRequestDTO request) {
        return promotionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        promotionService.delete(id);
    }
}
