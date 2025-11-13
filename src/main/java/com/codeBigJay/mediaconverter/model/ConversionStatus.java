package com.codeBigJay.mediaconverter.model;

public class ConversionStatus {
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private final String id;
    private volatile State state;
    private volatile String message;
    private volatile int progressPercent; // estimated percent 0-100
    private volatile String outputFilename; // relative or absolute path

    public ConversionStatus(String id) {
        this.id = id;
        this.state = State.QUEUED;
        this.message = "Queued";
        this.progressPercent = 0;
    }

    public String getId() { return id; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }
    public String getOutputFilename() { return outputFilename; }
    public void setOutputFilename(String outputFilename) { this.outputFilename = outputFilename; }
}