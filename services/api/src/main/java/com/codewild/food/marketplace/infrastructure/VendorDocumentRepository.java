package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorDocumentRepository extends JpaRepository<VendorDocument, UUID> {
}
