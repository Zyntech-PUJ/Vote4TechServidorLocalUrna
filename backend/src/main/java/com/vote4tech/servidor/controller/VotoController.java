package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.CreateVotoDto;
import com.vote4tech.servidor.service.VotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/voto")
@RequiredArgsConstructor
@Tag(name = "Voto", description = "Registro de votos en urna")
public class VotoController {

    private final VotoService votoService;

    @PostMapping("/votar")
    @Operation(summary = "Registrar un voto")
    public ResponseEntity<Map<String, String>> votar(@RequestBody CreateVotoDto dto) {
        String votoId = votoService.votar(dto);
        return ResponseEntity.ok(Map.of("votoId", votoId, "status", "REGISTRADO"));
    }

    @GetMapping("/ya-voto/{cedula}/{idEleccion}")
    @Operation(summary = "Verificar si un ciudadano ya votó")
    public ResponseEntity<Boolean> yaVoto(
            @PathVariable String cedula,
            @PathVariable Long idEleccion) {
        return ResponseEntity.ok(votoService.yaVoto(cedula, idEleccion));
    }
}
