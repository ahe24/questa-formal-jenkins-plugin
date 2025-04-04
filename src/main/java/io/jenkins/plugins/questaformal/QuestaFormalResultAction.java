package io.jenkins.plugins.questaformal;

import hudson.model.Action;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public class QuestaFormalResultAction implements Action {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final String lintDesign;
    private final String lintTimestamp;
    private final double qualityScore;
    private final int lintErrorCount;
    private final int lintWarningCount;
    private final int lintInfoCount;
    private final List<QuestaFormalResult.CheckItem> lintChecks;
    private final boolean debugMode;

    public QuestaFormalResultAction(String lintDesign, String lintTimestamp, double qualityScore,
                                  int lintErrorCount, int lintWarningCount, int lintInfoCount,
                                  List<QuestaFormalResult.CheckItem> lintChecks, boolean debugMode) {
        this.lintDesign = lintDesign;
        this.lintTimestamp = lintTimestamp;
        this.qualityScore = qualityScore;
        this.lintErrorCount = lintErrorCount;
        this.lintWarningCount = lintWarningCount;
        this.lintInfoCount = lintInfoCount;
        this.lintChecks = lintChecks;
        this.debugMode = debugMode;
    }

    @Override
    public String getIconFileName() {
        return "graph.gif";
    }

    @Override
    public String getDisplayName() {
        return "Questa Formal Results";
    }

    @Override
    public String getUrlName() {
        return "questaformal-result";
    }

    public String getLintDesign() {
        return lintDesign;
    }

    public String getLintTimestamp() {
        return lintTimestamp;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public int getLintErrorCount() {
        return lintErrorCount;
    }

    public int getLintWarningCount() {
        return lintWarningCount;
    }

    public int getLintInfoCount() {
        return lintInfoCount;
    }

    public List<QuestaFormalResult.CheckItem> getAllLintChecks() {
        return lintChecks;
    }

    public List<QuestaFormalResult.CheckItem> getLintErrors() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Error"))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<QuestaFormalResult.CheckItem> getLintWarnings() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Warning"))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<QuestaFormalResult.CheckItem> getLintInfo() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Info"))
                .collect(java.util.stream.Collectors.toList());
    }

    public double getQualityScorePercentage() {
        return qualityScore;
    }

    public String getDebugInfo() {
        return String.format(
            "Design: %s, Timestamp: %s, Quality Score: %.1f, Errors: %d, Warnings: %d, Info: %d",
            lintDesign, lintTimestamp, qualityScore, lintErrorCount, lintWarningCount, lintInfoCount
        );
    }

    public void logToConsole(String message) {
        if (debugMode) {
            System.out.println("[Questa Formal Debug] " + message);
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String lintErrorsJson() {
        try {
            return mapper.writeValueAsString(getLintErrors());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public String lintWarningsJson() {
        try {
            return mapper.writeValueAsString(getLintWarnings());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public String lintInfoJson() {
        try {
            return mapper.writeValueAsString(getLintInfo());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
} 