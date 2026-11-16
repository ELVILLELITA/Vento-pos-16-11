package com.vento.pos.config;

import com.vento.pos.repository.ProductoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductoDataInitializer implements ApplicationRunner {

    private final ProductoRepository productoRepository;

    public ProductoDataInitializer(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        productoRepository.activarProductosExistentes();
    }
}
