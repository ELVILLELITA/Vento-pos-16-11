package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "correlativos_operacion", uniqueConstraints = @UniqueConstraint(
        name = "uk_correlativo_sucursal_estacion_tipo",
        columnNames = {"sucursal", "estacion", "tipo_operacion"}))
public class CorrelativoOperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String sucursal;

    @Column(nullable = false, length = 10)
    private String estacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 3)
    private TipoOperacion tipoOperacion;

    @Column(name = "ultimo_numero", nullable = false)
    private long ultimoNumero;

    protected CorrelativoOperacion() {
    }

    public CorrelativoOperacion(String sucursal, String estacion, TipoOperacion tipoOperacion) {
        this.sucursal = sucursal;
        this.estacion = estacion;
        this.tipoOperacion = tipoOperacion;
    }

    public long siguienteNumero() {
        if (ultimoNumero >= 999_999) {
            throw new IllegalStateException("Se agotó el correlativo de " + tipoOperacion + ".");
        }
        return ++ultimoNumero;
    }
}
