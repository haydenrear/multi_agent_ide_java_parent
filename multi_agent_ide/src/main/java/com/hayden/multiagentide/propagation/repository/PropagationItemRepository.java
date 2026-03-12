package com.hayden.multiagentide.propagation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropagationItemRepository extends JpaRepository<PropagationItemEntity, Long> {
    Optional<PropagationItemEntity> findByItemId(String itemId);
    List<PropagationItemEntity> findByStatusOrderByCreatedAtDesc(String status);
    List<PropagationItemEntity> findBySourceNodeIdAndStatusOrderByCreatedAtDesc(String sourceNodeId, String status);
    Optional<PropagationItemEntity> findFirstByCorrelationKeyAndStatusOrderByCreatedAtDesc(String correlationKey, String status);
}
