package com.vento.pos.service;

import com.vento.pos.dto.CargaMasivaResultadoDTO;
import com.vento.pos.entity.Producto;
import com.vento.pos.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CargaMasivaService {

    private static final String CATEGORIA_POR_DEFECTO = "General";
    private final ProductoRepository productoRepository;

    public CargaMasivaService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional
    public CargaMasivaResultadoDTO cargarTexto(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return new CargaMasivaResultadoDTO(0, 0, List.of("El contenido de texto está vacío."));
        }
        List<String> errores = new ArrayList<>();
        List<Producto> productos = new ArrayList<>();
        Set<String> nombres = new HashSet<>();
        int[] total = {0};
        try (BufferedReader reader = new BufferedReader(new StringReader(contenido))) {
            List<String> lineas = reader.lines()
                    .map(String::trim)
                    .filter(linea -> !linea.isEmpty())
                    .collect(Collectors.toList());
            boolean encabezado = true;
            for (int index = 0; index < lineas.size(); index++) {
                String linea = lineas.get(index);
                if (encabezado && esEncabezado(linea)) {
                    encabezado = false;
                    continue;
                }
                encabezado = false;
                total[0]++;
                validarFila(linea, total[0], nombres, productos, errores);
            }
        } catch (IOException exception) {
            return new CargaMasivaResultadoDTO(0, 0, List.of("No se pudo leer el contenido."));
        }
        if (!productos.isEmpty()) {
            productoRepository.saveAll(productos);
            productoRepository.flush();
        }
        return new CargaMasivaResultadoDTO(total[0], productos.size(), List.copyOf(errores));
    }

    @Transactional
    public CargaMasivaResultadoDTO cargarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return new CargaMasivaResultadoDTO(0, 0, List.of("El archivo de texto está vacío."));
        }
        try {
            return cargarTexto(new String(archivo.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new CargaMasivaResultadoDTO(0, 0, List.of("No se pudo leer el archivo de texto."));
        }
    }

    private void validarFila(String linea, int numero, Set<String> nombres, List<Producto> productos,
                             List<String> errores) {
        String delimitador = linea.contains("\t") ? "\t" : ",";
        String[] columnas = linea.split(delimitador, -1);
        if (columnas.length != 5) {
            errores.add("Fila " + numero + ": se esperan Codigo, Nombre, Precio, Stock y Categoria.");
            return;
        }
        String codigo = columnas[0].trim();
        String nombre = columnas[1].trim();
        String precioTexto = columnas[2].trim();
        String stockTexto = columnas[3].trim();
        String categoria = columnas[4].trim();
        if (codigo.isBlank() || nombre.isBlank() || precioTexto.isBlank() || stockTexto.isBlank()) {
            errores.add("Fila " + numero + ": Codigo, Nombre, Precio y Stock son obligatorios.");
            return;
        }
        if (codigo.length() > 80 || nombre.length() > 150 || categoria.length() > 60) {
            errores.add("Fila " + numero + ": se excedió la longitud permitida.");
            return;
        }
        try {
            BigDecimal precio = new BigDecimal(precioTexto);
            int stock = Integer.parseInt(stockTexto);
            if (precio.signum() <= 0 || stock < 0) {
                errores.add("Fila " + numero + ": Precio debe ser mayor que cero y Stock no puede ser negativo.");
                return;
            }
            String clave = nombre.toLowerCase(Locale.ROOT);
            if (!nombres.add(clave) || productoRepository.existsByNombreIgnoreCaseAndActivoTrue(nombre)) {
                errores.add("Fila " + numero + ": ya existe un producto activo con ese nombre.");
                return;
            }
            Producto producto = new Producto(nombre, precio.setScale(2, RoundingMode.HALF_UP),
                    categoria.isBlank() ? CATEGORIA_POR_DEFECTO : categoria, false, stock);
            producto.setCodigoBarras(codigo);
            productos.add(producto);
        } catch (NumberFormatException exception) {
            errores.add("Fila " + numero + ": Precio o Stock tiene un formato inválido.");
        }
    }

    private boolean esEncabezado(String linea) {
        String normalizada = linea.toLowerCase(Locale.ROOT);
        return normalizada.startsWith("codigo\t") || normalizada.startsWith("codigo,");
    }
}
