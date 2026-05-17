package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.HotspotConfigDto;
import com.vote4tech.servidor.dto.LoginRequestDto;
import com.vote4tech.servidor.dto.LoginResponseDto;
import com.vote4tech.servidor.dto.ServerInfoDto;
import com.vote4tech.servidor.entity.HotspotConfig;
import com.vote4tech.servidor.entity.RegistradorLocal;
import com.vote4tech.servidor.repository.HotspotConfigRepository;
import com.vote4tech.servidor.repository.RegistradorLocalRepository;
import com.vote4tech.servidor.service.DispositivoTracker;
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

/**
 * Las apps Android consultan este endpoint al conectarse a la LAN
 * para verificar que el servidor está activo y obtener su versión.
 */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Información del servidor LAN y configuración hotspot")
public class ConfigController {

    private final RegistradorLocalRepository registradorRepo;
    private final HotspotConfigRepository hotspotRepo;
    private final com.vote4tech.servidor.repository.MesaRepository mesaRepo;
    private final DispositivoTracker dispositivoTracker;
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
    @Operation(summary = "Autenticar registrador electoral (para acceder a configuración desde urna)")
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
    @Operation(summary = "Listar mesas de votación disponibles en este servidor")
    public ResponseEntity<List<Map<String, Object>>> getMesas() {
        List<Map<String, Object>> result = mesaRepo.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("idMesa", m.getIdMesa());
                    map.put("numero", m.getNumero());
                    map.put("tipo", m.getTipo().name());
                    map.put("centro", m.getCentroVotacion() != null ? m.getCentroVotacion().getNombre() : "");
                    return map;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dispositivos")
    @Operation(summary = "Listar dispositivos que han hecho peticiones al servidor en los ultimos 30 minutos")
    public ResponseEntity<List<Map<String, String>>> getDispositivos() {
        return ResponseEntity.ok(dispositivoTracker.getActivos(30));
    }

    @DeleteMapping("/dispositivos/{ip}")
    @Operation(summary = "Desconectar un dispositivo por IP")
    public ResponseEntity<Void> desconectarDispositivo(@PathVariable String ip) {
        dispositivoTracker.remover(java.net.URLDecoder.decode(ip, java.nio.charset.StandardCharsets.UTF_8));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dispositivos")
    @Operation(summary = "Desconectar todos los dispositivos")
    public ResponseEntity<Void> desconectarTodos() {
        dispositivoTracker.removerTodos();
        return ResponseEntity.noContent().build();
    }
}