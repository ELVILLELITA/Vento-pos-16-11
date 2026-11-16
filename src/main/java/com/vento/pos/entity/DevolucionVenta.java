package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devoluciones_venta")
public class DevolucionVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folio_devolucion", nullable = false, unique = true, length = 32)
    private String folioDevolucion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(length = 250)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected DevolucionVenta() {
    }

    public DevolucionVenta(String folioDevolucion, Venta venta, Usuario usuario, BigDecimal importe, String motivo) {
        this.folioDevolucion = folioDevolucion;
        this.venta = venta;
        this.usuario = usuario;
        this.importe = importe;
        this.motivo = motivo;
    }

    @PrePersist
    void prePersist() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFolioDevolucion() { return folioDevolucion; }
    public Venta getVenta() { return venta; }
    public Usuario getUsuario() { return usuario; }
    public BigDecimal getImporte() { return importe; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFecha() { return fecha; }
}
