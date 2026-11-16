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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "turnos_caja")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "monto_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoInicial;

    @Column(name = "efectivo_esperado", precision = 12, scale = 2)
    private BigDecimal efectivoEsperado;

    @Column(name = "efectivo_real", precision = 12, scale = 2)
    private BigDecimal efectivoReal;

    @Column(precision = 12, scale = 2)
    private BigDecimal variacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoTurno estado = EstadoTurno.OPEN;

    @Column(length = 500)
    private String observaciones;

    @Version
    @Column(name = "version_lock")
    private Long version;

    protected Turno() {
    }

    public Turno(Usuario usuario, BigDecimal montoInicial, String observaciones) {
        this.usuario = usuario;
        this.montoInicial = montoInicial;
        this.observaciones = observaciones;
    }

    @PrePersist
    void prePersist() {
        if (fechaApertura == null) fechaApertura = LocalDateTime.now();
    }

    public void cerrar(LocalDateTime fechaCierre, BigDecimal efectivoEsperado, BigDecimal efectivoReal, String observaciones) {
        this.fechaCierre = fechaCierre;
        this.efectivoEsperado = efectivoEsperado;
        this.efectivoReal = efectivoReal;
        this.variacion = efectivoReal.subtract(efectivoEsperado);
        this.estado = EstadoTurno.CLOSED;
        if (observaciones != null && !observaciones.isBlank()) {
            this.observaciones = this.observaciones == null || this.observaciones.isBlank()
                    ? observaciones.trim()
                    : this.observaciones + "\nCierre: " + observaciones.trim();
        }
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public BigDecimal getMontoInicial() { return montoInicial; }
    public BigDecimal getEfectivoEsperado() { return efectivoEsperado; }
    public BigDecimal getEfectivoReal() { return efectivoReal; }
    public BigDecimal getVariacion() { return variacion; }
    public EstadoTurno getEstado() { return estado; }
    public String getObservaciones() { return observaciones; }
}
