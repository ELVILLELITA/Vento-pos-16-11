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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String numeroOrden;

    @Column(name = "folio_interno", unique = true, length = 32)
    private String folioInterno;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal iva;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    @Column(name = "descuento_automatico", precision = 12, scale = 2)
    private BigDecimal descuentoAutomatico = BigDecimal.ZERO;

    @Column(name = "descuento_manual", precision = 12, scale = 2)
    private BigDecimal descuentoManual = BigDecimal.ZERO;

    @Column(name = "promocion_aplicada", length = 120)
    private String promocionAplicada;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "metodo_pago", length = 20)
    private String metodoPago = "Efectivo";

    @Column(name = "comprador_nombre", length = 150)
    private String compradorNombre;

    @Column(name = "folio_venta_origen", length = 32)
    private String folioVentaOrigen;

    @Column(name = "credito_devolucion_consumido", nullable = false, columnDefinition = "boolean default false")
    private boolean creditoDevolucionConsumido;

    @Column(name = "es_exenta", columnDefinition = "boolean default false")
    private boolean esExenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private TipoComprobante tipoComprobante = TipoComprobante.CONSUMIDOR_FINAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descuento_aprobado_por_id")
    private Usuario descuentoAprobadoPor;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();

    protected Venta() {
    }

    public Venta(BigDecimal subtotal, BigDecimal iva, BigDecimal descuento, BigDecimal total, String estado) {
        this.subtotal = subtotal;
        this.iva = iva;
        this.descuento = descuento;
        this.total = total;
        this.estado = estado;
    }

    @PrePersist
    void prePersist() {
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
        if (tipoComprobante == null) {
            tipoComprobante = TipoComprobante.CONSUMIDOR_FINAL;
        }
    }

    public void agregarDetalle(DetalleVenta detalle) {
        detalles.add(detalle);
        detalle.setVenta(this);
    }

    public Long getId() {
        return id;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public String getFolioInterno() {
        return folioInterno;
    }

    public void setFolioInterno(String folioInterno) {
        this.folioInterno = folioInterno;
    }

    public void setNumeroOrden(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public BigDecimal getDescuentoAutomatico() {
        return descuentoAutomatico == null ? BigDecimal.ZERO : descuentoAutomatico;
    }

    public void setDescuentoAutomatico(BigDecimal descuentoAutomatico) {
        this.descuentoAutomatico = descuentoAutomatico;
    }

    public BigDecimal getDescuentoManual() {
        return descuentoManual == null ? BigDecimal.ZERO : descuentoManual;
    }

    public void setDescuentoManual(BigDecimal descuentoManual) {
        this.descuentoManual = descuentoManual;
    }

    public String getPromocionAplicada() {
        return promocionAplicada;
    }

    public void setPromocionAplicada(String promocionAplicada) {
        this.promocionAplicada = promocionAplicada;
    }

    public Usuario getDescuentoAprobadoPor() {
        return descuentoAprobadoPor;
    }

    public void setDescuentoAprobadoPor(Usuario descuentoAprobadoPor) {
        this.descuentoAprobadoPor = descuentoAprobadoPor;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getEstado() {
        return estado;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getCompradorNombre() {
        return compradorNombre;
    }

    public void setCompradorNombre(String compradorNombre) {
        this.compradorNombre = compradorNombre;
    }

    public String getFolioVentaOrigen() {
        return folioVentaOrigen;
    }

    public void setFolioVentaOrigen(String folioVentaOrigen) {
        this.folioVentaOrigen = folioVentaOrigen;
    }

    public boolean isCreditoDevolucionConsumido() {
        return creditoDevolucionConsumido;
    }

    public void setCreditoDevolucionConsumido(boolean creditoDevolucionConsumido) {
        this.creditoDevolucionConsumido = creditoDevolucionConsumido;
    }

    public boolean isEsExenta() {
        return esExenta;
    }

    public void setEsExenta(boolean esExenta) {
        this.esExenta = esExenta;
    }

    public TipoComprobante getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(TipoComprobante tipoComprobante) {
        this.tipoComprobante = tipoComprobante == null ? TipoComprobante.CONSUMIDOR_FINAL : tipoComprobante;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public List<DetalleVenta> getDetalles() {
        return detalles;
    }
}
