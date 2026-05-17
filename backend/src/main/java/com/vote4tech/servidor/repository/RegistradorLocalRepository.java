package com.vote4tech.servidor.repository;

import com.vote4tech.servidor.entity.RegistradorLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegistradorLocalRepository extends JpaRepository<RegistradorLocal, Long> {
    Optional<RegistradorLocal> findByUsernameAndActivoTrue(String username);
}
