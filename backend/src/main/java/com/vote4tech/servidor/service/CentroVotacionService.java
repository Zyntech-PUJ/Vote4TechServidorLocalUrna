package com.vote4tech.servidor.service;

import com.vote4tech.servidor.dto.CentroVotacionDto;
import com.vote4tech.servidor.entity.CentroVotacion;
import com.vote4tech.servidor.entity.ServidorConfig;
import com.vote4tech.servidor.exception.BusinessException;
import com.vote4tech.servidor.exception.ResourceNotFoundException;
import com.vote4tech.servidor.repository.CentroVotacionRepository;
import com.vote4tech.servidor.repository.MesaRepository;
import com.vote4tech.servidor.repository.ServidorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CentroVotacionService {

    private final CentroVotacionRepository centroRepo;
    private final ServidorConfigRepository servidorConfigRepo;
    private final MesaRepository mesaRepo;

    @Value("${central.db.url:}")
    private String centralDbUrl;
    @Value("${central.db.username:}")
    private String centralDbUser;
    @Value("${central.db.password:}")
    private String centralDbPassword;

    public String getServerId() {
        ServidorConfig cfg = servidorConfigRepo.findById(1L).orElse(null);
        if (cfg == null || cfg.getServerId() == null) {
            cfg = cfg != null ? cfg : ServidorConfig.builder().id(1L).build();
            cfg.setServerId(UUID.randomUUID().toString());
            servidorConfigRepo.save(cfg);
        }
        return cfg.getServerId();
    }

    public Long getCentroAsignadoId() {
        return servidorConfigRepo.findById(1L)
                .map(ServidorConfig::getIdCentroAsignado)
                .orElse(null);
    }

    public List<CentroVotacionDto> findAll() {
        String myId = getServerId();
        return centroRepo.findAll().stream().map(c -> {
            CentroVotacionDto dto = new CentroVotacionDto();
            dto.setIdCentroVotacion(c.getIdCentroVotacion());
            dto.setNombre(c.getNombre());
            dto.setDireccion(c.getDireccion());
            dto.setTotalMesas((int) mesaRepo.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getActivo())
                            && m.getCentroVotacion() != null
                            && m.getCentroVotacion().getIdCentroVotacion().equals(c.getIdCentroVotacion()))
                    .count());
            if (c.getServidorId() == null) {
                dto.setEstado("DISPONIBLE");
            } else if (c.getServidorId().equals(myId)) {
                dto.setEstado("PROPIO");
            } else {
                dto.setEstado("OCUPADO");
            }
            return dto;
        }).toList();
    }

    public Optional<CentroVotacionDto> getCentroActual() {
        String myId = getServerId();
        return centroRepo.findByServidorId(myId).map(c -> {
            CentroVotacionDto dto = new CentroVotacionDto();
            dto.setIdCentroVotacion(c.getIdCentroVotacion());
            dto.setNombre(c.getNombre());
            dto.setDireccion(c.getDireccion());
            dto.setEstado("PROPIO");
            dto.setTotalMesas((int) mesaRepo.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getActivo())
                            && m.getCentroVotacion() != null
                            && m.getCentroVotacion().getIdCentroVotacion().equals(c.getIdCentroVotacion()))
                    .count());
            return dto;
        });
    }

    @Transactional
    public CentroVotacionDto asignar(Long idCentro) {
        String myId = getServerId();

        CentroVotacion centro = centroRepo.findById(idCentro)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado: " + idCentro));

        if (centro.getServidorId() != null && !centro.getServidorId().equals(myId)) {
            throw new BusinessException("El centro ya está asignado a otro servidor.");
        }

        // Release previous claim
        centroRepo.releaseByServidorId(myId);

        // Claim new center
        centro.setServidorId(myId);
        centroRepo.save(centro);

        // Persist assignment in servidor_config
        ServidorConfig cfg = servidorConfigRepo.findById(1L)
                .orElse(ServidorConfig.builder().id(1L).serverId(myId).build());
        cfg.setIdCentroAsignado(idCentro);
        servidorConfigRepo.save(cfg);

        // Write-back to central DB (best effort)
        writeCentralAssignment(myId, idCentro);

        CentroVotacionDto dto = new CentroVotacionDto();
        dto.setIdCentroVotacion(centro.getIdCentroVotacion());
        dto.setNombre(centro.getNombre());
        dto.setDireccion(centro.getDireccion());
        dto.setEstado("PROPIO");
        return dto;
    }

    @Transactional
    public void liberar() {
        String myId = getServerId();
        centroRepo.releaseByServidorId(myId);

        ServidorConfig cfg = servidorConfigRepo.findById(1L).orElse(null);
        if (cfg != null) {
            cfg.setIdCentroAsignado(null);
            servidorConfigRepo.save(cfg);
        }

        writeCentralRelease(myId);
    }

    private void writeCentralAssignment(String serverId, Long idCentro) {
        if (centralDbUrl == null || centralDbUrl.isBlank()) return;
        try {
            JdbcTemplate cj = buildCentralJdbc();
            cj.execute("ALTER TABLE centro_votacion ADD COLUMN IF NOT EXISTS servidor_id VARCHAR(36)");
            cj.update("UPDATE centro_votacion SET servidor_id = NULL WHERE servidor_id = ?", serverId);
            cj.update("UPDATE centro_votacion SET servidor_id = ? WHERE id_centro_votacion = ? AND (servidor_id IS NULL OR servidor_id = ?)",
                    serverId, idCentro, serverId);
        } catch (Exception ignored) {}
    }

    private void writeCentralRelease(String serverId) {
        if (centralDbUrl == null || centralDbUrl.isBlank()) return;
        try {
            JdbcTemplate cj = buildCentralJdbc();
            cj.update("UPDATE centro_votacion SET servidor_id = NULL WHERE servidor_id = ?", serverId);
        } catch (Exception ignored) {}
    }

    private JdbcTemplate buildCentralJdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(centralDbUrl);
        ds.setUsername(centralDbUser);
        ds.setPassword(centralDbPassword);
        return new JdbcTemplate(ds);
    }
}
