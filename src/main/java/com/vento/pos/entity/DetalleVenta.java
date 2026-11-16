package com.vento.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "detalles_venta")
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "cantidad_devuelta")
    private Integer cantidadDevuelta = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_automatico", precision = 12, scale = 2)
    private BigDecimal descuentoAutomatico = BigDecimal.ZERO;

    @Column(name = "descuento_manual", precision = 12, scale = 2)
    private BigDecimal descuentoManual = BigDecimal.ZERO;

    @Column(name = "promocion_aplicada", length = 120)
    private String promocionAplicada;

    protected DetalleVenta() {
    }

    public DetalleVenta(Producto producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public Long getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public void setVenta(Venta venta) {
        this.venta = venta;
    }

    public Producto getProducto() {
        return producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public Integer getCantidadDevuelta() {
        return cantidadDevuelta == null ? 0 : cantidadDevuelta;
    }

    public void setCantidadDevuelta(Integer cantidadDevuelta) {
        this.cantidadDevuelta = cantidadDevuelta;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDescuentoAutomatico() {
        return descuentoAutomatico == null ? BigDecimal.ZERO : descuentoAutomatico;
    }

    public BigDecimal getDescuentoManual() {
        return descuentoManual == null ? BigDecimal.ZERO : descuentoManual;
    }

    public String getPromocionAplicada() {
        return promocionAplicada;
    }

    public void setDescuentos(BigDecimal automatico, BigDecimal manual, String promocion) {
        this.descuentoAutomatico = automatico;
        this.descuentoManual = manual;
        this.promocionAplicada = promocion;
    }
}
