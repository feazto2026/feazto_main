package com.codewild.food.operations.infrastructure;

import com.codewild.food.operations.domain.SupportTicket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
  List<SupportTicket> findByStatus(SupportTicket.Status status);
  List<SupportTicket> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);
  List<SupportTicket> findByAssignedToAndStatus(UUID assignedTo, SupportTicket.Status status);
}
