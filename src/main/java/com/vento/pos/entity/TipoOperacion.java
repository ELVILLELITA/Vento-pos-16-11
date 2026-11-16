package com.vento.pos.entity;

public enum TipoOperacion {
    CA("CA"),
    DE("DE"),
    TME("TME"),
    TMS("TMS"),
    AC("AC"),
    CX("CX"),
    CZ("CZ"),
    EC("EC"),
    SC("SC"),
    AI("AI"),
    CP("CP");

    private final String codigo;

    TipoOperacion(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
