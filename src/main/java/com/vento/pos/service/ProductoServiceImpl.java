package com.vento.pos.service;

import com.vento.pos.dto.ProductoRequestDTO;
import com.vento.pos.dto.ProductoResponseDTO;
import com.vento.pos.entity.Producto;
import com.vento.pos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoServiceImpl(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listar(String categoria, String busqueda, boolean incluirInactivos) {
        String filtroCategoria = limpiarOpcional(categoria);
        String filtroBusqueda = limpiarOpcional(busqueda);
        return productoRepository.buscar(filtroCategoria, filtroBusqueda, incluirInactivos)
                .stream()
                .map(ProductoServiceImpl::convertir)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtener(Long id) {
        return convertir(buscarProducto(id));
    }

    @Override
    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO request) {
        String nombre = request.nombre().trim();
        validarNombreDisponible(nombre, null);
        Producto producto = new Producto(
                nombre,
                request.precio(),
                request.categoria().trim(),
                request.favorito() != null && request.favorito(),
                request.stock());
        producto.setActivo(request.activo() == null || request.activo());
        producto.setPrecioIncluyeIva(request.precioIncluyeIva() == null || request.precioIncluyeIva());
        producto.setCodigoBarras(limpiarOpcional(request.codigoBarras()));
        return convertir(productoRepository.save(producto));
    }

    @Override
    @Transactional
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO request) {
        Producto producto = buscarProducto(id);
        String nombre = request.nombre().trim();
        validarNombreDisponible(nombre, id);

        producto.setNombre(nombre);
        producto.setPrecio(request.precio());
        producto.setCategoria(request.categoria().trim());
        if (request.favorito() != null) {
            producto.setFavorito(request.favorito());
        }
        producto.setStock(request.stock());
        if (request.activo() != null) {
            producto.setActivo(request.activo());
        }
        if (request.precioIncluyeIva() != null) {
            producto.setPrecioIncluyeIva(request.precioIncluyeIva());
        }
        producto.setCodigoBarras(limpiarOpcional(request.codigoBarras()));
        return convertir(productoRepository.save(producto));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Producto producto = buscarProducto(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        return productoRepository.findActiveCategories();
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe el producto " + id + "."));
    }

    private void validarNombreDisponible(String nombre, Long idActual) {
        boolean duplicado = idActual == null
                ? productoRepository.existsByNombreIgnoreCaseAndActivoTrue(nombre)
                : productoRepository.existsByNombreIgnoreCaseAndActivoTrueAndIdNot(nombre, idActual);
        if (duplicado) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe un producto activo con ese nombre.");
        }
    }

    private static String limpiarOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProductoResponseDTO convertir(Producto producto) {
        return new ProductoResponseDTO(
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getCategoria(),
                producto.getFavorito(),
                producto.getStock(),
                producto.getActivo(),
                producto.isPrecioIncluyeIva(),
                producto.getCodigoBarras());
    }
}
