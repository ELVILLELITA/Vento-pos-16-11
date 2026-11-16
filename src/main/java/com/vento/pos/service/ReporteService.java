package com.vento.pos.service;

import com.vento.pos.dto.CategoriaVentaDTO;
import com.vento.pos.dto.DashboardSummaryDTO;
import com.vento.pos.dto.EmpleadoVentaDTO;
import com.vento.pos.dto.ProductoVentaDTO;
import com.vento.pos.dto.ProductoVentaAggregateDTO;
import com.vento.pos.repository.DevolucionVentaRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.ReporteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReporteService {

    private static final BigDecimal CERO = BigDecimal.ZERO;
    private static final int LIMITE_PRODUCTOS = 5;

    private final ReporteRepository reporteRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final ProductoRepository productoRepository;

    public ReporteService(
            ReporteRepository reporteRepository,
            DevolucionVentaRepository devolucionRepository,
            ProductoRepository productoRepository) {
        this.reporteRepository = reporteRepository;
        this.devolucionRepository = devolucionRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDTO obtenerDashboard(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        LocalDateTime desde = fechaInicio;
        LocalDateTime hasta = fechaFin;
        if (desde == null && hasta == null) {
            LocalDate hoy = LocalDate.now();
            desde = hoy.atStartOfDay();
            hasta = hoy.atTime(23, 59, 59, 999_999_999);
        }

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior o igual a la fecha final.");
        }
        BigDecimal totalVentas = valor(reporteRepository.totalVentas(desde, hasta));
        Long transacciones = reporteRepository.totalTransacciones(desde, hasta);
        BigDecimal devoluciones = valor(devolucionRepository.totalPorFecha(desde, hasta));
        BigDecimal ticketMedio = transacciones == null || transacciones == 0
                ? CERO.setScale(2, RoundingMode.HALF_UP)
                : totalVentas.divide(BigDecimal.valueOf(transacciones), 2, RoundingMode.HALF_UP);

        List<ProductoVentaAggregateDTO> productosVendidos = reporteRepository.productosVendidos(desde, hasta);
        List<ProductoVentaDTO> productos = completarCatalogo(productosVendidos);
        BigDecimal totalPorCategoria = productos.stream()
                .map(ProductoVentaDTO::totalRevenue)
                .reduce(CERO, BigDecimal::add);
        List<CategoriaVentaDTO> categorias = agruparCategorias(productos, totalPorCategoria);

        List<ProductoVentaDTO> topProductos = productos.stream()
                .filter(producto -> producto.unitsSold() > 0)
                .sorted(Comparator.comparing(ProductoVentaDTO::totalRevenue).reversed()
                        .thenComparing(ProductoVentaDTO::unitsSold, Comparator.reverseOrder())
                        .thenComparing(ProductoVentaDTO::productName, String.CASE_INSENSITIVE_ORDER))
                .limit(LIMITE_PRODUCTOS)
                .toList();
        List<ProductoVentaDTO> menosVendidos = productos.stream()
                .sorted(Comparator.comparing(ProductoVentaDTO::unitsSold)
                        .thenComparing(ProductoVentaDTO::totalRevenue)
                        .thenComparing(ProductoVentaDTO::productName, String.CASE_INSENSITIVE_ORDER))
                .limit(LIMITE_PRODUCTOS)
                .toList();

        return new DashboardSummaryDTO(
                totalVentas,
                transacciones == null ? 0L : transacciones,
                ticketMedio,
                devoluciones,
                reporteRepository.ventasPorEmpleado(desde, hasta),
                topProductos,
                menosVendidos,
                categorias,
                reporteRepository.ventasPorMetodoPago(desde, hasta));
    }

    @Transactional(readOnly = true)
    public byte[] exportar(String tipo, String periodo, LocalDateTime desde, LocalDateTime hasta) {
        if (!"excel".equalsIgnoreCase(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo está disponible la exportación Excel.");
        }
        LocalDateTime[] rango = resolverRango(periodo, desde, hasta);
        DashboardSummaryDTO dashboard = obtenerDashboard(rango[0], rango[1]);
        return generarExcel(dashboard, rango[0], rango[1]);
    }

    private LocalDateTime[] resolverRango(String periodo, LocalDateTime desde, LocalDateTime hasta) {
        if ("personalizado".equalsIgnoreCase(periodo)) {
            if (desde == null || hasta == null || desde.isAfter(hasta)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido.");
            }
            return new LocalDateTime[]{desde, hasta};
        }
        LocalDate hoy = LocalDate.now();
        return switch (periodo == null ? "hoy" : periodo.toLowerCase()) {
            case "semana" -> new LocalDateTime[]{hoy.minusDays(hoy.getDayOfWeek().getValue() - 1).atStartOfDay(),
                    hoy.atTime(23, 59, 59, 999_999_999)};
            case "mes" -> new LocalDateTime[]{hoy.withDayOfMonth(1).atStartOfDay(),
                    hoy.atTime(23, 59, 59, 999_999_999)};
            default -> new LocalDateTime[]{hoy.atStartOfDay(), hoy.atTime(23, 59, 59, 999_999_999)};
        };
    }

    private byte[] generarExcel(DashboardSummaryDTO dashboard, LocalDateTime desde, LocalDateTime hasta) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet resumen = workbook.createSheet("Resumen");
            filaExcel(resumen, 0, "Indicador", "Valor");
            filaExcel(resumen, 1, "Periodo", desde + " - " + hasta);
            filaExcel(resumen, 2, "Total ventas", dashboard.totalVentas());
            filaExcel(resumen, 3, "Total transacciones", dashboard.totalTransacciones());
            filaExcel(resumen, 4, "Ticket medio", dashboard.ticketMedio());
            filaExcel(resumen, 5, "Total devoluciones", dashboard.totalDevoluciones());

            Sheet empleados = workbook.createSheet("Empleados");
            filaExcel(empleados, 0, "Cajero", "Transacciones", "Ventas");
            int fila = 1;
            for (var row : dashboard.ventasPorEmpleado()) {
                filaExcel(empleados, fila++, row.cashierName(), row.salesCount(), row.totalAmount());
            }

            Sheet productos = workbook.createSheet("Productos");
            filaExcel(productos, 0, "Producto", "Categoria", "Unidades", "Ventas");
            fila = 1;
            for (var row : dashboard.topProductos()) {
                filaExcel(productos, fila++, row.productName(), row.category(), row.unitsSold(), row.totalRevenue());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", exception);
        }
    }

    private void filaExcel(Sheet sheet, int indice, Object... valores) {
        Row row = sheet.createRow(indice);
        for (int columna = 0; columna < valores.length; columna++) {
            row.createCell(columna).setCellValue(Objects.toString(valores[columna], ""));
        }
    }

    private List<ProductoVentaDTO> completarCatalogo(List<ProductoVentaAggregateDTO> vendidos) {
        Map<Long, ProductoVentaAggregateDTO> porProducto = new HashMap<>();
        for (ProductoVentaAggregateDTO producto : vendidos) {
            porProducto.put(producto.productId(), producto);
        }
        List<ProductoVentaDTO> catalogo = new ArrayList<>();
        productoRepository.findAllByOrderByNombreAsc().forEach(producto -> {
            ProductoVentaAggregateDTO vendido = porProducto.get(producto.getId());
            catalogo.add(vendido == null
                    ? new ProductoVentaDTO(producto.getNombre(), producto.getCategoria(), 0L, CERO)
                    : new ProductoVentaDTO(
                            producto.getNombre(),
                            producto.getCategoria(),
                            vendido.unitsSold(),
                            valor(vendido.totalRevenue())));
        });
        return catalogo;
    }

    private List<CategoriaVentaDTO> agruparCategorias(
            List<ProductoVentaDTO> productos, BigDecimal totalPorCategoria) {
        Map<String, BigDecimal> importes = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        productos.forEach(producto -> importes.merge(
                producto.category(), producto.totalRevenue(), BigDecimal::add));
        return importes.entrySet().stream()
                .filter(entrada -> entrada.getValue().signum() > 0)
                .map(entrada -> new CategoriaVentaDTO(
                        entrada.getKey(),
                        entrada.getValue(),
                        totalPorCategoria.signum() == 0
                                ? CERO.setScale(2, RoundingMode.HALF_UP)
                                : entrada.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(totalPorCategoria, 2, RoundingMode.HALF_UP)))
                .toList();
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? CERO.setScale(2, RoundingMode.HALF_UP) : valor;
    }
}
