package com.antiam.repository;

import com.antiam.domain.CasServiceTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasServiceTicketRepository extends JpaRepository<CasServiceTicket, UUID> {
    Optional<CasServiceTicket> findByTicketHash(String ticketHash);
}
