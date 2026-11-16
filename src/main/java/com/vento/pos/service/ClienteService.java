package com.vento.pos.service;

import com.vento.pos.dto.ClienteRequestDTO;
import com.vento.pos.dto.ClienteResponseDTO;
import com.vento.pos.entity.Cliente;
import com.vento.pos.entity.TipoDocumento;
import com.vento.pos.repository.ClienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listar(String q) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return clienteRepository.buscarActivos(query).stream().map(this::convertir).toList();
    }

    @Transactional
    public ClienteResponseDTO crear(ClienteRequestDTO request) {
        Cliente cliente = new Cliente(
                request.nombre().trim(),
                limpiar(request.documento()),
                limpiar(request.telefono()),
                limpiar(request.email()),
                limpiar(request.direccion()),
                request.tipoDocumento() == null ? TipoDocumento.DUI : request.tipoDocumento());
        cliente.setNrc(limpiar(request.nrc()));
        cliente.setGiro(limpiar(request.giro()));
        cliente.setCodigoActividad(limpiar(request.codigoActividad()));
        return convertir(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponseDTO actualizar(Long id, ClienteRequestDTO request) {
        Cliente cliente = buscarActivo(id);
        cliente.setNombre(request.nombre().trim());
        cliente.setDocumento(limpiar(request.documento()));
        cliente.setNrc(limpiar(request.nrc()));
        cliente.setGiro(limpiar(request.giro()));
        cliente.setCodigoActividad(limpiar(request.codigoActividad()));
        cliente.setTelefono(limpiar(request.telefono()));
        cliente.setEmail(limpiar(request.email()));
        cliente.setDireccion(limpiar(request.direccion()));
        cliente.setTipoDocumento(request.tipoDocumento() == null ? TipoDocumento.DUI : request.tipoDocumento());
        return convertir(cliente);
    }

    @Transactional
    public void deshabilitar(Long id) {
        buscarActivo(id).setActivo(false);
    }

    private Cliente buscarActivo(Long id) {
        return clienteRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el cliente " + id + "."));
    }

    private String limpiar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ClienteResponseDTO convertir(Cliente cliente) {
        return new ClienteResponseDTO(
                cliente.getId(), cliente.getNombre(), cliente.getDocumento(), cliente.getNrc(), cliente.getGiro(),
                cliente.getCodigoActividad(), cliente.getTelefono(),
                cliente.getEmail(), cliente.getDireccion(), cliente.getTipoDocumento(),
                cliente.getFechaRegistro(), cliente.isActivo());
    }
}
