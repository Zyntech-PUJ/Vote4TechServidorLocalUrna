package com.vote4tech.servidor.controller;

import com.vote4tech.servidor.dto.CiudadanoDto;
import com.vote4tech.servidor.dto.LoginRequestDto;
import com.vote4tech.servidor.dto.LoginResponseDto;
import com.vote4tech.servidor.entity.JuradoLocal;
import com.vote4tech.servidor.repository.JuradoLocalRepository;
import com.vote4tech.servidor.service.CiudadanoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
@Tag(name = "Consultas", description = "Consultas del jurado de votación")
public class ConsultasController {

    private final JuradoLocalRepository juradoRepo;
    private final CiudadanoService ciudadanoService;

    @PostMapping("/jurado/login")
    @Operation(summary = "Autenticar jurado de votación con cédula y contraseña")
    public ResponseEntity<LoginResponseDto> loginJurado(@RequestBody LoginRequestDto request) {
        Optional<JuradoLocal> jurado = juradoRepo.findByCedulaAndActivoTrue(request.getUsername());
        if (jurado.isEmpty() || !jurado.get().getPassword().equals(request.getPassword())) {
            return ResponseEntity.status(401)
                    .body(new LoginResponseDto(false, null, "Credenciales incorrectas"));
        }
        return ResponseEntity.ok(new LoginResponseDto(true, jurado.get().getNombre(), "Acceso concedido"));
    }

    @GetMapping("/ciudadanos-urna")
    @Operation(summary = "Listar ciudadanos habilitados para votar en urna")
    public ResponseEntity<List<CiudadanoDto>> getCiudadanosUrna() {
        return ResponseEntity.ok(ciudadanoService.findAllUrna());
    }
}
