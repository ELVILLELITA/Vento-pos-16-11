package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.ColumnDefault;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nombre;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String categoria;

    @Column(name = "codigo_barras", length = 80)
    private String codigoBarras;

    @NotNull
    @Column(nullable = false)
    private Boolean favorito = false;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stock = 0;

    @Column
    private Boolean activo = true;

    @Column(name = "precio_incluye_iva", nullable = false)
    @ColumnDefault("true")
    private boolean precioIncluyeIva = true;

    protected Producto() {
    }

    public Producto(String nombre, BigDecimal precio, String categoria, Boolean favorito, Integer stock) {
        this.nombre = nombre;
        this.precio = precio;
        this.categoria = categoria;
        this.favorito = favorito;
        this.stock = stock;
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public Boolean getFavorito() {
        return favorito;
    }

    public void setFavorito(Boolean favorito) {
        this.favorito = favorito;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public boolean isPrecioIncluyeIva() {
        return precioIncluyeIva;
    }

    public void setPrecioIncluyeIva(boolean precioIncluyeIva) {
        this.precioIncluyeIva = precioIncluyeIva;
    }

    @PrePersist
    void prePersist() {
        if (activo == null) activo = true;
    }
}
