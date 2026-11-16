package com.vento.pos.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compras")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folio_interno", nullable = false, unique = true, length = 32)
    private String folioInterno;

    @Column(name = "numero_factura_proveedor", length = 80)
    private String numeroFacturaProveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCompra> detalles = new ArrayList<>();

    protected Compra() {
    }

    public Compra(String folioInterno, String numeroFacturaProveedor, Proveedor proveedor, BigDecimal total, Long usuarioId) {
        this.folioInterno = folioInterno;
        this.numeroFacturaProveedor = numeroFacturaProveedor;
        this.proveedor = proveedor;
        this.total = total;
        this.usuarioId = usuarioId;
    }

    public void agregarDetalle(DetalleCompra detalle) {
        detalles.add(detalle);
        detalle.setCompra(this);
    }

    @PrePersist
    void prePersist() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFolioInterno() { return folioInterno; }
    public String getNumeroFacturaProveedor() { return numeroFacturaProveedor; }
    public Proveedor getProveedor() { return proveedor; }
    public LocalDateTime getFecha() { return fecha; }
    public BigDecimal getTotal() { return total; }
    public Long getUsuarioId() { return usuarioId; }
    public List<DetalleCompra> getDetalles() { return detalles; }
}
