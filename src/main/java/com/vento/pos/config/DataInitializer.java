package com.vento.pos.config;

import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Rol;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner inicializarDatos(ProductoRepository productoRepository, UsuarioRepository usuarioRepository) {
        return args -> {
            if (productoRepository.count() == 0) {
                productoRepository.saveAll(List.of(
                        producto("Espresso", "42.00", "Café"),
                        producto("Americano", "48.00", "Café"),
                        producto("Latte", "62.00", "Café"),
                        producto("Café helado", "68.00", "Bebidas"),
                        producto("Té verde", "45.00", "Bebidas"),
                        producto("Jugo de naranja", "55.00", "Bebidas"),
                        producto("Pan artesanal", "38.00", "Panadería"),
                        producto("Croissant", "44.00", "Panadería"),
                        producto("Pastel del día", "72.00", "Postres")
                ));
            }

            if (usuarioRepository.count() == 0) {
                usuarioRepository.saveAll(List.of(
                        new Usuario("admin", null, "Administrador General", Rol.ROLE_ADMIN, true),
                        new Usuario("cajero", null, "Cajero Turno 1", Rol.ROLE_USER, true)
                ));
            }
        };
    }

    private static Producto producto(String nombre, String precio, String categoria) {
        return new Producto(nombre, new BigDecimal(precio), categoria, true, 1000);
    }
}
