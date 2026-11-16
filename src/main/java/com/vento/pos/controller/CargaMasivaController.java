package com.vento.pos.controller;

import com.vento.pos.dto.CargaMasivaResultadoDTO;
import com.vento.pos.service.CargaMasivaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/productos")
public class CargaMasivaController {

    private final CargaMasivaService cargaMasivaService;

    public CargaMasivaController(CargaMasivaService cargaMasivaService) {
        this.cargaMasivaService = cargaMasivaService;
    }

    @PostMapping(value = "/carga-masiva-texto", consumes = {
            MediaType.TEXT_PLAIN_VALUE, "text/csv", MediaType.APPLICATION_OCTET_STREAM_VALUE
    })
    public CargaMasivaResultadoDTO cargarTexto(@RequestBody String contenido) {
        return cargaMasivaService.cargarTexto(contenido);
    }

    @PostMapping(value = "/carga-masiva-texto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargaMasivaResultadoDTO cargarArchivo(@RequestPart("archivo") MultipartFile archivo) {
        return cargaMasivaService.cargarArchivo(archivo);
    }
}
