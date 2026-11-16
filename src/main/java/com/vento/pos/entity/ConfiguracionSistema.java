package com.vento.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "configuracion_sistema")
public class ConfiguracionSistema {

    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 150)
    private String nombreEstablecimiento = "Café Vento";

    @Column(length = 250)
    private String direccion;

    @Column(length = 40)
    private String telefono;

    @Column(nullable = false, length = 10)
    private String codigoSucursal = "S01";

    @Column(nullable = false, length = 10)
    private String codigoEstacion = "C01";

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = new BigDecimal("13.00");

    @Column(nullable = false, length = 5)
    private String monedaSimbolo = "$";

    @Column(nullable = false, length = 250)
    private String piePaginaTicket = "¡Gracias por su compra!";

    @Column(length = 2)
    private String ambienteDte = "00";

    @Column(length = 30)
    private String nitEmisor;

    @Column(length = 30)
    private String nrcEmisor;

    @Column(length = 10)
    private String codigoActividadEmisor;

    @Column(length = 10000)
    private String clavePrivadaCertificado;

    @Column(length = 1000)
    private String passwordCertificado;

    @Column(length = 500)
    private String urlFirmador = "http://localhost:8080";

    @Column(length = 15)
    private String codigoPuntoVentaMh;

    public ConfiguracionSistema() {
    }

    public Long getId() { return id; }
    public String getNombreEstablecimiento() { return nombreEstablecimiento; }
    public void setNombreEstablecimiento(String nombreEstablecimiento) { this.nombreEstablecimiento = nombreEstablecimiento; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCodigoSucursal() { return codigoSucursal; }
    public void setCodigoSucursal(String codigoSucursal) { this.codigoSucursal = codigoSucursal; }
    public String getCodigoEstacion() { return codigoEstacion; }
    public void setCodigoEstacion(String codigoEstacion) { this.codigoEstacion = codigoEstacion; }
    public BigDecimal getPorcentajeIva() { return porcentajeIva; }
    public void setPorcentajeIva(BigDecimal porcentajeIva) { this.porcentajeIva = porcentajeIva; }
    public String getMonedaSimbolo() { return monedaSimbolo; }
    public void setMonedaSimbolo(String monedaSimbolo) { this.monedaSimbolo = monedaSimbolo; }
    public String getPiePaginaTicket() { return piePaginaTicket; }
    public void setPiePaginaTicket(String piePaginaTicket) { this.piePaginaTicket = piePaginaTicket; }
    public String getAmbienteDte() { return ambienteDte; }
    public void setAmbienteDte(String ambienteDte) { this.ambienteDte = ambienteDte; }
    public String getNitEmisor() { return nitEmisor; }
    public void setNitEmisor(String nitEmisor) { this.nitEmisor = nitEmisor; }
    public String getNrcEmisor() { return nrcEmisor; }
    public void setNrcEmisor(String nrcEmisor) { this.nrcEmisor = nrcEmisor; }
    public String getCodigoActividadEmisor() { return codigoActividadEmisor; }
    public void setCodigoActividadEmisor(String codigoActividadEmisor) { this.codigoActividadEmisor = codigoActividadEmisor; }
    public String getClavePrivadaCertificado() { return clavePrivadaCertificado; }
    public void setClavePrivadaCertificado(String clavePrivadaCertificado) { this.clavePrivadaCertificado = clavePrivadaCertificado; }
    public String getPasswordCertificado() { return passwordCertificado; }
    public void setPasswordCertificado(String passwordCertificado) { this.passwordCertificado = passwordCertificado; }
    public String getUrlFirmador() { return urlFirmador; }
    public void setUrlFirmador(String urlFirmador) { this.urlFirmador = urlFirmador; }
    public String getCodigoPuntoVentaMh() { return codigoPuntoVentaMh; }
    public void setCodigoPuntoVentaMh(String codigoPuntoVentaMh) { this.codigoPuntoVentaMh = codigoPuntoVentaMh; }
}
