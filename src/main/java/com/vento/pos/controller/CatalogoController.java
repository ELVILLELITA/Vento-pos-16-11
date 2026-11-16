package com.vento.pos.controller;

import com.vento.pos.dto.ActividadEconomicaDTO;
import com.vento.pos.service.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogos")
public class CatalogoController {

    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/actividades-economicas")
    public List<ActividadEconomicaDTO> actividadesEconomicas(@RequestParam(required = false) String q) {
        return catalogo.buscar(q);
    }
}
