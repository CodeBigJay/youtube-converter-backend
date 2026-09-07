package com.codeBigJay.mediaconverter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private volatile String outputFilename; // kept for backward compatibility: first output file
    private final List<String> outputFilenames = Collections.synchronizedList(new ArrayList<>());

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

    /** Backward-compatible single-file accessor: returns the first output file, if any. */
    public String getOutputFilename() { return outputFilename; }
    public void setOutputFilename(String outputFilename) { this.outputFilename = outputFilename; }

    /** All output files produced by this conversion (more than one for a playlist). */
    public List<String> getOutputFilenames() { return new ArrayList<>(outputFilenames); }

    public void addOutputFilename(String path) {
        outputFilenames.add(path);
        if (outputFilename == null) {
            outputFilename = path;
        }
    }

    public int getOutputCount() { return outputFilenames.size(); }
}
