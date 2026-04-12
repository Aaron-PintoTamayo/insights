package com.h2ai.insights.repository;

import com.h2ai.insights.entity.PredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, Long> {
}
