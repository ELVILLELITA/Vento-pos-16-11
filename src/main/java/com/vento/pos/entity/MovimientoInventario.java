package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folio_movimiento", nullable = false, unique = true, length = 32)
    private String folioMovimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "folio_referencia", length = 32)
    private String folioReferencia;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 3)
    private TipoOperacion tipoOperacion;

    @Column(nullable = false, length = 250)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;

    @Column(name = "stock_nuevo", nullable = false)
    private Integer stockNuevo;

    protected MovimientoInventario() {
    }

    public MovimientoInventario(
            String folioMovimiento,
            String folioReferencia,
            Producto producto,
            Usuario usuario,
            Integer cantidad,
            TipoMovimiento tipo,
            TipoOperacion tipoOperacion,
            String motivo,
            Integer stockAnterior,
            Integer stockNuevo) {
        this.folioMovimiento = folioMovimiento;
        this.folioReferencia = folioReferencia;
        this.producto = producto;
        this.usuario = usuario;
        this.cantidad = cantidad;
        this.tipo = tipo;
        this.tipoOperacion = tipoOperacion;
        this.motivo = motivo;
        this.stockAnterior = stockAnterior;
        this.stockNuevo = stockNuevo;
    }

    @PrePersist
    void prePersist() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFolioMovimiento() { return folioMovimiento; }
    public String getFolioReferencia() { return folioReferencia; }
    public Producto getProducto() { return producto; }
    public Usuario getUsuario() { return usuario; }
    public Integer getCantidad() { return cantidad; }
    public TipoMovimiento getTipo() { return tipo; }
    public TipoOperacion getTipoOperacion() { return tipoOperacion; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFecha() { return fecha; }
    public Integer getStockAnterior() { return stockAnterior; }
    public Integer getStockNuevo() { return stockNuevo; }
}
