package com.h2ai.insights.repository;

import com.h2ai.insights.entity.PredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, Long>, JpaSpecificationExecutor<PredictionRecord> {

    List<PredictionRecord> findByActualOutcomeIsNotNull();
}
