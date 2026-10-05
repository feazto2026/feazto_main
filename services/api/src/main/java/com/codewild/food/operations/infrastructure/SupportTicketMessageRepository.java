package com.codewild.food.operations.infrastructure;

import com.codewild.food.operations.domain.Review;
import com.codewild.food.operations.domain.SupportTicketMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {
  List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
