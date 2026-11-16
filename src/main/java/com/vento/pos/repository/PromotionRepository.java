package com.vento.pos.repository;

import com.vento.pos.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByPriorityDescIdAsc();
    List<Promotion> findAllByActiveTrueOrderByPriorityDescIdAsc();
}
