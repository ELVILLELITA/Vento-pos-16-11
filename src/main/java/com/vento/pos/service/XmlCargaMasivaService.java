package com.vento.pos.service;

import com.vento.pos.dto.CargaMasivaResultadoDTO;
import com.vento.pos.entity.Producto;
import com.vento.pos.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class XmlCargaMasivaService {

    private static final String CATEGORIA_POR_DEFECTO = "General";
    private final ProductoRepository productoRepository;

    public XmlCargaMasivaService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional
    public CargaMasivaResultadoDTO cargar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return resultadoConError("El archivo XML es obligatorio y no puede estar vacío.");
        }
        try {
            return cargar(archivo.getBytes());
        } catch (IOException exception) {
            return resultadoConError("No se pudo leer el archivo XML.");
        }
    }

    @Transactional
    public CargaMasivaResultadoDTO cargar(byte[] xml) {
        List<String> errores = new ArrayList<>();
        if (xml == null || xml.length == 0) {
            return resultadoConError("El contenido XML es obligatorio y no puede estar vacío.");
        }

        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception exception) {
            return resultadoConError("El XML no es válido o no pudo procesarse.");
        }

        Element root = document.getDocumentElement();
        if (root == null || !"CargaMasiva".equals(root.getTagName())) {
            return resultadoConError("La raíz del XML debe ser <CargaMasiva>.");
        }
        Element encabezado = child(root, "Encabezado");
        Element itemsElement = child(root, "Items");
        if (encabezado == null) {
            errores.add("Falta la estructura <Encabezado>.");
        }
        if (itemsElement == null) {
            errores.add("Falta la estructura <Items>.");
            return new CargaMasivaResultadoDTO(0, 0, List.copyOf(errores));
        }

        String categoria = texto(encabezado, "Categoria");
        categoria = categoria == null ? CATEGORIA_POR_DEFECTO : categoria;
        if (categoria.length() > 60) {
            errores.add("La categoría del encabezado supera los 60 caracteres.");
            categoria = CATEGORIA_POR_DEFECTO;
        }

        List<Element> itemElements = children(itemsElement, "Item");
        List<Producto> productos = new ArrayList<>();
        Set<String> nombresEnArchivo = new HashSet<>();
        int total = itemElements.size();
        for (int index = 0; index < itemElements.size(); index++) {
            Element item = itemElements.get(index);
            int numero = index + 1;
            String codigo = texto(item, "Codigo");
            String nombre = texto(item, "Nombre");
            String precioTexto = texto(item, "Precio");
            String stockTexto = texto(item, "Stock");
            if (codigo == null || nombre == null || precioTexto == null || stockTexto == null) {
                errores.add("Item " + numero + ": Codigo, Nombre, Precio y Stock son obligatorios.");
                continue;
            }
            if (nombre.length() > 150 || codigo.length() > 80) {
                errores.add("Item " + numero + ": Codigo o Nombre supera la longitud permitida.");
                continue;
            }
            BigDecimal precio;
            Integer stock;
            try {
                precio = new BigDecimal(precioTexto);
                stock = Integer.valueOf(stockTexto);
            } catch (NumberFormatException exception) {
                errores.add("Item " + numero + ": Precio o Stock tiene un formato inválido.");
                continue;
            }
            if (precio.signum() <= 0) {
                errores.add("Item " + numero + ": Precio debe ser mayor que cero.");
                continue;
            }
            if (stock < 0) {
                errores.add("Item " + numero + ": Stock no puede ser negativo.");
                continue;
            }
            String nombreClave = nombre.toLowerCase(Locale.ROOT);
            if (!nombresEnArchivo.add(nombreClave)
                    || productoRepository.existsByNombreIgnoreCaseAndActivoTrue(nombre)) {
                errores.add("Item " + numero + ": ya existe un producto activo con el nombre indicado.");
                continue;
            }
            Producto producto = new Producto(nombre, precio.setScale(2, RoundingMode.HALF_UP), categoria, false, stock);
            producto.setCodigoBarras(codigo);
            productos.add(producto);
        }

        if (!productos.isEmpty()) {
            productoRepository.saveAll(productos);
            productoRepository.flush();
        }
        return new CargaMasivaResultadoDTO(total, productos.size(), List.copyOf(errores));
    }

    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String texto(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) {
            return null;
        }
        String value = element.getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CargaMasivaResultadoDTO resultadoConError(String error) {
        return new CargaMasivaResultadoDTO(0, 0, List.of(error));
    }
}
