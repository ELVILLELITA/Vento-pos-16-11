package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "documentos_dte")
public class DteDocumento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venta_id", nullable = false, unique = true)
    private Long ventaId;

    @Column(name = "tipo_dte", nullable = false, length = 2)
    private String tipoDte;

    @Column(name = "codigo_generacion", nullable = false, unique = true, length = 36)
    private String codigoGeneracion;

    @Column(name = "firmado_en", nullable = false)
    private LocalDateTime firmadoEn;

    @Lob
    @Column(name = "documento_firmado", nullable = false)
    private String documentoFirmado;

    protected DteDocumento() {
    }

    public DteDocumento(Long ventaId, String tipoDte, String codigoGeneracion,
                        LocalDateTime firmadoEn, String documentoFirmado) {
        this.ventaId = ventaId;
        this.tipoDte = tipoDte;
        this.codigoGeneracion = codigoGeneracion;
        this.firmadoEn = firmadoEn;
        this.documentoFirmado = documentoFirmado;
    }

    public Long getId() { return id; }
    public Long getVentaId() { return ventaId; }
    public String getTipoDte() { return tipoDte; }
    public String getCodigoGeneracion() { return codigoGeneracion; }
    public LocalDateTime getFirmadoEn() { return firmadoEn; }
    public String getDocumentoFirmado() { return documentoFirmado; }
}
