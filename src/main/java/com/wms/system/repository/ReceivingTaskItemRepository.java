package com.wms.system.repository;

import com.wms.system.entity.ReceivingTaskItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceivingTaskItemRepository extends JpaRepository<ReceivingTaskItem, Long> {
    List<ReceivingTaskItem> findByReceivingTaskIdOrderByIdAsc(Long receivingTaskId);
}
