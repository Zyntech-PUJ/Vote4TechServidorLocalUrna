package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.CentroVotacionDto;
import com.vote4tech.servidor.dto.HotspotConfigDto;
import com.vote4tech.servidor.dto.LoginRequestDto;
import com.vote4tech.servidor.dto.LoginResponseDto;
import com.vote4tech.servidor.dto.ServerInfoDto;
import com.vote4tech.servidor.entity.HotspotConfig;
import com.vote4tech.servidor.entity.RegistradorLocal;
import com.vote4tech.servidor.repository.HotspotConfigRepository;
import com.vote4tech.servidor.repository.MesaRepository;
import com.vote4tech.servidor.repository.RegistradorLocalRepository;
import com.vote4tech.servidor.service.CentroVotacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Información del servidor LAN y configuración hotspot")
public class ConfigController {

    private final RegistradorLocalRepository registradorRepo;
    private final HotspotConfigRepository hotspotRepo;
    private final MesaRepository mesaRepo;
    private final CentroVotacionService centroService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/ping")
    @Operation(summary = "Verificar que el servidor está activo")
    public ResponseEntity<ServerInfoDto> ping() {
        return ResponseEntity.ok(ServerInfoDto.builder()
                .version("1.0.0")
                .status("OK")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @PostMapping("/registrador/login")
    @Operation(summary = "Autenticar registrador electoral")
    public ResponseEntity<LoginResponseDto> loginRegistrador(@RequestBody LoginRequestDto request) {
        Optional<RegistradorLocal> reg = registradorRepo.findByUsernameAndActivoTrue(request.getUsername());
        if (reg.isEmpty() || !passwordEncoder.matches(request.getPassword(), reg.get().getPassword())) {
            return ResponseEntity.status(401)
                    .body(new LoginResponseDto(false, null, "Credenciales incorrectas"));
        }
        return ResponseEntity.ok(new LoginResponseDto(true, reg.get().getNombre(), "Acceso concedido"));
    }

    @GetMapping("/hotspot")
    @Operation(summary = "Obtener configuración del hotspot WiFi")
    public ResponseEntity<HotspotConfigDto> getHotspot() {
        List<HotspotConfig> configs = hotspotRepo.findAll();
        if (configs.isEmpty()) {
            return ResponseEntity.ok(new HotspotConfigDto());
        }
        HotspotConfig c = configs.get(0);
        HotspotConfigDto dto = new HotspotConfigDto();
        dto.setSsid(c.getSsid());
        dto.setPassword(c.getPassword());
        dto.setCanal(c.getCanal());
        dto.setPuerto(c.getPuerto());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/hotspot")
    @Operation(summary = "Guardar configuración del hotspot WiFi")
    public ResponseEntity<HotspotConfigDto> saveHotspot(@RequestBody HotspotConfigDto dto) {
        List<HotspotConfig> configs = hotspotRepo.findAll();
        HotspotConfig config = configs.isEmpty() ? new HotspotConfig() : configs.get(0);
        config.setSsid(dto.getSsid());
        config.setPassword(dto.getPassword());
        if (dto.getCanal() != null) config.setCanal(dto.getCanal());
        if (dto.getPuerto() != null) config.setPuerto(dto.getPuerto());
        hotspotRepo.save(config);
        dto.setCanal(config.getCanal());
        dto.setPuerto(config.getPuerto());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/mesas")
    @Operation(summary = "Listar mesas de votación del centro asignado a este servidor")
    public ResponseEntity<List<Map<String, Object>>> getMesas() {
        Long centroAsignado = centroService.getCentroAsignadoId();
        List<Map<String, Object>> result = mesaRepo.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .filter(m -> centroAsignado == null || (m.getCentroVotacion() != null
                        && m.getCentroVotacion().getIdCentroVotacion().equals(centroAsignado)))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("idMesa", m.getIdMesa());
                    map.put("numero", m.getNumero());
                    map.put("tipo", m.getTipo().name());
                    map.put("centro", m.getCentroVotacion() != null ? m.getCentroVotacion().getNombre() : "");
                    map.put("idCentro", m.getCentroVotacion() != null ? m.getCentroVotacion().getIdCentroVotacion() : null);
                    return map;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Centro de Votación ────────────────────────────────────────────────────

    @GetMapping("/centros")
    @Operation(summary = "Listar centros de votación con su disponibilidad")
    public ResponseEntity<List<CentroVotacionDto>> getCentros() {
        return ResponseEntity.ok(centroService.findAll());
    }

    @PostMapping("/centros/{id}/asignar")
    @Operation(summary = "Asignar este servidor a un centro de votación")
    public ResponseEntity<?> asignarCentro(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(centroService.asignar(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/centros/asignacion")
    @Operation(summary = "Liberar la asignación de centro de este servidor")
    public ResponseEntity<Void> liberarCentro() {
        centroService.liberar();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/centro-actual")
    @Operation(summary = "Obtener el centro de votación asignado a este servidor")
    public ResponseEntity<?> getCentroActual() {
        return centroService.getCentroActual()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("mensaje", "Sin centro asignado")));
    }
}
