package com.reservoir.model;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReservoirSubmission {
    @JsonProperty("reservoirName")
    private String reservoirName;
    
    @JsonProperty("waterLevel")
    private String waterLevel;
    
    @JsonProperty("inflow")
    private String inflow;
    
    @JsonProperty("outflow")
    private String outflow;

    @JsonProperty("waterStorage")
    private String waterStorage;

    @JsonProperty("submissionDate")
    private String submissionDate;

    public ReservoirSubmission() {}

    public ReservoirSubmission(String reservoirName, String waterLevel, String inflow, String outflow, String waterStorage) {
        this.reservoirName = reservoirName;
        this.waterLevel = waterLevel;
        this.inflow = inflow;
        this.outflow = outflow;
        this.waterStorage = waterStorage;
        this.submissionDate = LocalDate.now().toString();
    }
    
    public String getReservoirName() {
        return reservoirName;
    }
    
    public void setReservoirName(String reservoirName) {
        this.reservoirName = reservoirName;
    }
    
    public String getWaterLevel() {
        return waterLevel;
    }
    
    public void setWaterLevel(String waterLevel) {
        this.waterLevel = waterLevel;
    }
    
    public String getInflow() {
        return inflow;
    }
    
    public void setInflow(String inflow) {
        this.inflow = inflow;
    }
    
    public String getOutflow() {
        return outflow;
    }
    
    public void setOutflow(String outflow) {
        this.outflow = outflow;
    }

    public String getWaterStorage() {
        return waterStorage;
    }

    public void setWaterStorage(String waterStorage) {
        this.waterStorage = waterStorage;
    }

    public String getSubmissionDate() {
        return submissionDate;
    }
    
    public void setSubmissionDate(String submissionDate) {
        this.submissionDate = submissionDate;
    }
}
