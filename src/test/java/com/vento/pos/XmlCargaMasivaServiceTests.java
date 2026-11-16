package com.vento.pos;

import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.service.XmlCargaMasivaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XmlCargaMasivaServiceTests {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private XmlCargaMasivaService service;

    @Test
    void cargaProductosValidosYReportaItemsInvalidos() {
        when(productoRepository.existsByNombreIgnoreCaseAndActivoTrue(anyString())).thenReturn(false);
        byte[] xml = """
                <CargaMasiva>
                  <Encabezado><Categoria>Bebidas</Categoria></Encabezado>
                  <Items>
                    <Item><Codigo>CAF-01</Codigo><Nombre>Café</Nombre><Precio>2.50</Precio><Stock>10</Stock></Item>
                    <Item><Codigo>BAD-01</Codigo><Nombre>Té</Nombre><Precio>0</Precio><Stock>2</Stock></Item>
                  </Items>
                </CargaMasiva>
                """.getBytes();

        var result = service.cargar(xml);

        assertEquals(2, result.totalProcesados());
        assertEquals(1, result.insercionesExitosas());
        assertEquals(1, result.errores().size());
        verify(productoRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(productoRepository).flush();
    }

    @Test
    void rechazaXmlConEntidadesExternas() {
        byte[] xml = """
                <!DOCTYPE CargaMasiva [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <CargaMasiva><Items>&xxe;</Items></CargaMasiva>
                """.getBytes();

        var result = service.cargar(xml);

        assertEquals(0, result.insercionesExitosas());
        assertEquals(1, result.errores().size());
        verify(productoRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
