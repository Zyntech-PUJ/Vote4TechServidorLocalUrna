package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.CiudadanoDto;
import com.vote4tech.servidor.service.CiudadanoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ciudadano")
@RequiredArgsConstructor
@Tag(name = "Ciudadano", description = "Validación de electores")
public class CiudadanoController {

    private final CiudadanoService ciudadanoService;

    @GetMapping("/{cedula}")
    @Operation(summary = "Buscar ciudadano por cédula")
    public ResponseEntity<CiudadanoDto> getByCedula(@PathVariable String cedula) {
        return ResponseEntity.ok(ciudadanoService.findByCedula(cedula));
    }

    @GetMapping("/domicilio")
    @Operation(summary = "Listar ciudadanos habilitados para voto domiciliario")
    public ResponseEntity<List<CiudadanoDto>> getDomicilio() {
        return ResponseEntity.ok(ciudadanoService.findAllDomicilio());
    }

    @GetMapping("/urna")
    @Operation(summary = "Listar ciudadanos habilitados para voto en urna (consultas del jurado)")
    public ResponseEntity<List<CiudadanoDto>> getUrna() {
        return ResponseEntity.ok(ciudadanoService.findAllUrna());
    }

    @GetMapping("/todos")
    @Operation(summary = "Listar todos los ciudadanos")
    public ResponseEntity<List<CiudadanoDto>> getTodos() {
        return ResponseEntity.ok(ciudadanoService.findAll());
    }
}
