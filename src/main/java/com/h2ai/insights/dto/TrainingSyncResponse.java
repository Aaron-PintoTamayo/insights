package com.h2ai.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TrainingSyncResponse {

    private int sentRecords;
    private int totalLabeledRecords;
    private String status;
}
