package com.vento.pos.controller;

import com.vento.pos.dto.CargaMasivaResultadoDTO;
import com.vento.pos.service.XmlCargaMasivaService;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/productos")
public class XmlCargaMasivaController {

    private final XmlCargaMasivaService xmlCargaMasivaService;

    public XmlCargaMasivaController(XmlCargaMasivaService xmlCargaMasivaService) {
        this.xmlCargaMasivaService = xmlCargaMasivaService;
    }

    @PostMapping(value = "/carga-masiva", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargaMasivaResultadoDTO cargar(@RequestPart("archivo") MultipartFile archivo) {
        return xmlCargaMasivaService.cargar(archivo);
    }

    @GetMapping(value = "/carga-masiva/plantilla", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> descargarPlantilla() {
        String plantilla = """
                Codigo	Nombre	Precio	Stock	Categoria
                PROD001	Café Ejemplo	45.00	100	Bebidas
                """;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("plantilla_carga_masiva.txt")
                                .build()
                                .toString())
                .body(plantilla);
    }
}
