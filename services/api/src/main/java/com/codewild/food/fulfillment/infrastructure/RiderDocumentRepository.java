package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.RiderDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderDocumentRepository extends JpaRepository<RiderDocument, UUID> {
}
