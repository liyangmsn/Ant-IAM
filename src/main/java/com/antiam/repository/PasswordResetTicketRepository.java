package com.antiam.repository;

import com.antiam.domain.PasswordResetTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTicketRepository extends JpaRepository<PasswordResetTicket, UUID> {
    Optional<PasswordResetTicket> findByTokenHash(String tokenHash);
}
