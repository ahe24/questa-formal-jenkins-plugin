package io.jenkins.plugins.questaformal;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestaFormalResult extends Recorder implements SimpleBuildStep {
    private static final String DEFAULT_LINT_REPORT_PATH = "Lint_Results/lint.rpt";
    private static final String DEFAULT_CDC_REPORT_PATH = "CDC_result/cdc.rpt";
    
    private final String lintReportPath;
    private final String cdcReportPath;
    private boolean debugMode;
    private List<CheckItem> lintChecks = new ArrayList<>();
    private String lintDesign = "";
    private String lintTimestamp = "";
    private double qualityScore = 0.0;
    private int lintErrorCount = 0;
    private int lintWarningCount = 0;
    private int lintInfoCount = 0;

    @DataBoundConstructor
    public QuestaFormalResult(String lintReportPath, String cdcReportPath) {
        this.lintReportPath = lintReportPath != null && !lintReportPath.trim().isEmpty() 
            ? lintReportPath.trim() 
            : DEFAULT_LINT_REPORT_PATH;
        this.cdcReportPath = cdcReportPath != null && !cdcReportPath.trim().isEmpty() 
            ? cdcReportPath.trim() 
            : DEFAULT_CDC_REPORT_PATH;
        this.debugMode = false;
    }

    public String getLintReportPath() {
        return lintReportPath;
    }

    public String getCdcReportPath() {
        return cdcReportPath;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        if (lintReportPath == null) {
            if (debugMode) {
                listener.error("Lint report path is null");
            }
            return;
        }

        if (debugMode) {
            listener.getLogger().println("\n=== Starting Lint Report Parsing ===");
            listener.getLogger().println("Report path: " + lintReportPath);
        }

        FilePath reportFile = workspace.child(lintReportPath);
        if (!reportFile.exists()) {
            listener.error("Lint report file not found: " + lintReportPath);
            return;
        }

        parseLintReport(reportFile, listener);
        
        // Create and add action
        QuestaFormalResultAction action = new QuestaFormalResultAction(
            lintDesign, lintTimestamp, qualityScore,
            lintErrorCount, lintWarningCount, lintInfoCount,
            lintChecks, debugMode
        );
        
        if (debugMode) {
            listener.getLogger().println("\n=== Adding QuestaFormalResultAction ===");
            listener.getLogger().println("Design: " + lintDesign);
            listener.getLogger().println("Timestamp: " + lintTimestamp);
            listener.getLogger().println("Quality Score: " + qualityScore);
            listener.getLogger().println("Error Count: " + lintErrorCount);
            listener.getLogger().println("Warning Count: " + lintWarningCount);
            listener.getLogger().println("Info Count: " + lintInfoCount);
            listener.getLogger().println("Total Checks: " + lintChecks.size());
        }
        
        run.addAction(action);
        
        if (debugMode) {
            listener.getLogger().println("Action added successfully");
        }
    }

    private void parseLintReport(FilePath reportFile, TaskListener listener) throws IOException, InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(reportFile.read(), StandardCharsets.UTF_8))) {
            String line;
            boolean isSummarySection = false;
            boolean isDetailsSection = false;
            String currentCategory = "";
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Parse header information
                if (line.startsWith("Design") && !line.contains("Quality Score")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length > 1) {
                        lintDesign = parts[1].trim();
                        if (debugMode) {
                            listener.getLogger().println("[Header] Found Design: " + lintDesign);
                        }
                    }
                }
                else if (line.startsWith("Timestamp")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length > 1) {
                        lintTimestamp = parts[1].trim();
                        if (debugMode) {
                            listener.getLogger().println("Found Timestamp: " + lintTimestamp);
                        }
                    }
                }
                else if (line.startsWith("Design Quality Score")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length > 1) {
                        String scoreStr = parts[1].trim().replace("%", "");
                        try {
                            qualityScore = Double.parseDouble(scoreStr);
                            if (debugMode) {
                                listener.getLogger().println("Found Quality Score: " + qualityScore);
                            }
                        } catch (NumberFormatException e) {
                            qualityScore = 0.0;
                            if (debugMode) {
                                listener.getLogger().println("Failed to parse Quality Score, defaulting to 0.0");
                            }
                        }
                    }
                }
                
                // Parse sections
                if (line.contains("Section 1 : Check Summary")) {
                    isSummarySection = true;
                    isDetailsSection = false;
                    if (debugMode) {
                        listener.getLogger().println("\n=== Entering Summary Section ===");
                    }
                    continue;
                }
                else if (line.contains("Section 2 : Check Details")) {
                    isSummarySection = false;
                    isDetailsSection = true;
                    if (debugMode) {
                        listener.getLogger().println("\n=== Entering Details Section ===");
                    }
                    continue;
                }
                
                // Parse summary section
                if (isSummarySection) {
                    // Parse category headers
                    if (line.startsWith("| Error")) {
                        Matcher m = Pattern.compile("Error\\s*\\((\\d+)\\)").matcher(line);
                        if (m.find()) {
                            lintErrorCount = Integer.parseInt(m.group(1));
                            currentCategory = "Error";
                            if (debugMode) {
                                listener.getLogger().println("[Summary] Found Error category with count: " + lintErrorCount);
                            }
                        }
                        continue;
                    }
                    else if (line.startsWith("| Warning")) {
                        Matcher m = Pattern.compile("Warning\\s*\\((\\d+)\\)").matcher(line);
                        if (m.find()) {
                            lintWarningCount = Integer.parseInt(m.group(1));
                            currentCategory = "Warning";
                            if (debugMode) {
                                listener.getLogger().println("Found Warning count: " + lintWarningCount);
                            }
                        }
                        continue;
                    }
                    else if (line.startsWith("| Info")) {
                        Matcher m = Pattern.compile("Info\\s*\\((\\d+)\\)").matcher(line);
                        if (m.find()) {
                            lintInfoCount = Integer.parseInt(m.group(1));
                            currentCategory = "Info";
                            if (debugMode) {
                                listener.getLogger().println("Found Info count: " + lintInfoCount);
                            }
                        }
                        continue;
                    }
                    else if (line.startsWith("| Resolved")) {
                        currentCategory = "Resolved";
                        continue;
                    }
                    
                    // Skip separator lines
                    if (line.startsWith("-") || line.startsWith("=")) {
                        continue;
                    }
                    
                    // Parse check items in summary
                    if (!line.startsWith("|") && line.contains(":")) {
                        String[] parts = line.trim().split("\\s*:\\s*");
                        if (parts.length == 2) {
                            String checkName = parts[0].trim();
                            try {
                                int checkCount = Integer.parseInt(parts[1].trim());
                                CheckItem newItem = new CheckItem(checkName, checkCount, "", currentCategory);
                                lintChecks.add(newItem);
                                
                                if (debugMode) {
                                    listener.getLogger().println(String.format("[Summary] Added check item: %s (count: %d, category: %s, id: %s)",
                                        checkName, checkCount, currentCategory, newItem.getId()));
                                }
                            } catch (NumberFormatException e) {
                                if (debugMode) {
                                    listener.getLogger().println("[Error] Failed to parse check count for: " + checkName);
                                }
                            }
                        }
                    }
                }
                
                // Parse details section
                else if (isDetailsSection) {
                    if (line.startsWith("| Error") || line.startsWith("| Warning") || 
                        line.startsWith("| Info") || line.startsWith("| Resolved")) {
                        currentCategory = line.substring(2, line.indexOf(" (")).trim();
                        continue;
                    }
                    
                    if (line.startsWith("Check:")) {
                        if (debugMode) {
                            listener.getLogger().println("\n[Details] Processing check: " + line);
                        }
                        
                        StringBuilder detailsBuilder = new StringBuilder();
                        String checkName = "";
                        String category = "";
                        
                        Matcher checkMatcher = Pattern.compile("Check:\\s*([^\\[]+)\\[Category:\\s*([^\\]]+)\\]\\s*\\((\\d+)\\)").matcher(line);
                        if (checkMatcher.find()) {
                            checkName = checkMatcher.group(1).trim();
                            category = checkMatcher.group(2).trim();
                            
                            // Read message line
                            String messageLine = reader.readLine();
                            if (messageLine != null && messageLine.trim().startsWith("[Message:")) {
                                String message = messageLine.trim();
                                message = message.substring(message.indexOf(":") + 1).trim();
                                if (message.endsWith("]")) {
                                    message = message.substring(0, message.length() - 1).trim();
                                }
                                
                                detailsBuilder.append(message).append("\n");
                                
                                if (debugMode) {
                                    listener.getLogger().println("[Details] Found message: " + message);
                                }
                                
                                // Skip separator line
                                reader.readLine();
                                
                                // Read occurrences
                                String instanceLine;
                                String lastSpecificDetails = "";
                                String lastOccurrenceFile = "";
                                
                                while ((instanceLine = reader.readLine()) != null) {
                                    instanceLine = instanceLine.trim();
                                    if (instanceLine.isEmpty() || instanceLine.startsWith("Check:") || 
                                        instanceLine.startsWith("=====") || instanceLine.startsWith("-----") ||
                                        instanceLine.startsWith("|")) {
                                        break;
                                    }
                                    
                                    if (debugMode) {
                                        listener.getLogger().println("[Details] Processing occurrence line: " + instanceLine);
                                    }
                                    
                                    // Parse occurrences
                                    if (instanceLine.contains(": [uninspected]") || instanceLine.startsWith(checkName + ":")) {
                                        if (debugMode) {
                                            listener.getLogger().println("[Debug] Processing main occurrence line: " + instanceLine);
                                        }
                                        
                                        String occurrence = instanceLine;
                                        if (instanceLine.contains("[uninspected]")) {
                                            occurrence = instanceLine.substring(instanceLine.indexOf("[uninspected]") + 13).trim();
                                        } else if (instanceLine.startsWith(checkName + ":")) {
                                            occurrence = instanceLine.substring(instanceLine.indexOf(":") + 1).trim();
                                        }
                                        
                                        String occurrenceFile = "";
                                        String occurrenceLine = "";
                                        
                                        int fileIndex = occurrence.indexOf("File '");
                                        int lineIndex = occurrence.indexOf("Line '");
                                        
                                        if (fileIndex > 0 && lineIndex > 0) {
                                            occurrenceFile = occurrence.substring(fileIndex + 6, occurrence.indexOf("'", fileIndex + 6));
                                            occurrenceLine = occurrence.substring(lineIndex + 6, occurrence.indexOf("'", lineIndex + 6));
                                            
                                            String specificDetails = occurrence.substring(0, fileIndex).trim();
                                            lastSpecificDetails = specificDetails;
                                            lastOccurrenceFile = occurrenceFile;
                                            
                                            // 다음 라인이 "more occurrence" 라인인지 확인
                                            String nextLine = reader.readLine();
                                            List<String> additionalLineNumbers = new ArrayList<>();
                                            
                                            while (nextLine != null && nextLine.trim().contains("more occurrence")) {
                                                String moreOccurrenceLine = nextLine.trim();
                                                if (debugMode) {
                                                    listener.getLogger().println("[Debug] Found additional occurrences line: " + moreOccurrenceLine);
                                                }
                                                
                                                // Extract line numbers from various formats
                                                Pattern linePattern = Pattern.compile("line\\s+(\\d+)");
                                                Matcher lineMatcher = linePattern.matcher(moreOccurrenceLine);
                                                
                                                while (lineMatcher.find()) {
                                                    additionalLineNumbers.add(lineMatcher.group(1));
                                                }
                                                
                                                if (additionalLineNumbers.isEmpty()) {
                                                    Pattern numPattern = Pattern.compile("\\b(\\d+)\\b");
                                                    Matcher numMatcher = numPattern.matcher(moreOccurrenceLine);
                                                    while (numMatcher.find()) {
                                                        additionalLineNumbers.add(numMatcher.group(1));
                                                    }
                                                }
                                                
                                                if (debugMode) {
                                                    listener.getLogger().println("[Debug] Found additional line numbers: " + String.join(", ", additionalLineNumbers));
                                                }
                                                
                                                nextLine = reader.readLine();
                                            }
                                            
                                            // 모든 라인 번호를 하나의 발생 항목으로 결합
                                            List<String> allLineNumbers = new ArrayList<>();
                                            allLineNumbers.add(occurrenceLine);
                                            allLineNumbers.addAll(additionalLineNumbers);
                                            
                                            String combinedLineNumbers = String.join(", ", allLineNumbers.stream()
                                                .map(num -> "'" + num + "'")
                                                .collect(java.util.stream.Collectors.toList()));
                                            
                                            String formattedOccurrence = String.format("%s (File: %s, Line: %s)",
                                                specificDetails.trim(), occurrenceFile, combinedLineNumbers);

                                            // Update check item
                                            for (CheckItem item : lintChecks) {
                                                if (item.getName().equals(checkName)) {
                                                    item.addOccurrence(formattedOccurrence);
                                                    if (debugMode) {
                                                        listener.getLogger().println("[Details] Added occurrence to " + checkName + ": " + formattedOccurrence);
                                                        listener.getLogger().println("[Debug] Current occurrences count for " + checkName + ": " + item.getOccurrences().size() + "/" + item.getCount());
                                                    }
                                                    break;
                                                }
                                            }
                                            
                                            // If we read past the additional occurrences, we need to go back one line
                                            if (nextLine != null && !nextLine.trim().isEmpty() && 
                                                !nextLine.trim().startsWith("Check:") && 
                                                !nextLine.trim().startsWith("=====") && 
                                                !nextLine.trim().startsWith("-----") &&
                                                !nextLine.trim().startsWith("|")) {
                                                instanceLine = nextLine;
                                            }
                                        }
                                    }
                                }
                                
                                // Update check item details
                                for (CheckItem item : lintChecks) {
                                    if (item.getName().equals(checkName)) {
                                        item.setDetails(detailsBuilder.toString().trim());
                                        if (debugMode) {
                                            listener.getLogger().println(String.format("[Details] Updated check item %s: (details: %s, occurrences: %d)",
                                                checkName, detailsBuilder.toString().trim(), item.getOccurrences().size()));
                                            listener.getLogger().println("[Debug] Item type: " + item.getType() + ", Category: " + category);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Final debug logging
            if (debugMode) {
                listener.getLogger().println("\n=== Final Parsing Results ===");
                listener.getLogger().println("Total Check Items: " + lintChecks.size());
                
                listener.getLogger().println("\nCheck Items by Category (Detailed):");
                for (CheckItem item : lintChecks) {
                    listener.getLogger().println(String.format("- %s [%s] (count: %d, occurrences: %d)",
                        item.getName(), item.getType(), item.getCount(), item.getOccurrences().size()));
                }
                
                List<CheckItem> errors = getLintErrors();
                List<CheckItem> warnings = getLintWarnings();
                List<CheckItem> infos = getLintInfo();
                
                listener.getLogger().println("\nCategory Counts:");
                listener.getLogger().println("Errors: " + errors.size() + " (Total items: " + errors.stream().mapToInt(CheckItem::getCount).sum() + ")");
                listener.getLogger().println("Warnings: " + warnings.size() + " (Total items: " + warnings.stream().mapToInt(CheckItem::getCount).sum() + ")");
                listener.getLogger().println("Info: " + infos.size() + " (Total items: " + infos.stream().mapToInt(CheckItem::getCount).sum() + ")");
            }
        } catch (IOException e) {
            if (debugMode) {
                listener.error("Failed to read lint report: " + e.getMessage());
                e.printStackTrace(listener.getLogger());
            }
        }
    }

    public List<CheckItem> getAllLintChecks() {
        return lintChecks;
    }

    public List<CheckItem> getLintErrors() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Error"))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<CheckItem> getLintWarnings() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Warning"))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<CheckItem> getLintInfo() {
        return lintChecks.stream()
                .filter(check -> check.getType().equals("Info"))
                .collect(java.util.stream.Collectors.toList());
    }

    @Symbol("questaFormalResult")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public FormValidation doCheckLintReportPath(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.ok("Using default path: " + DEFAULT_LINT_REPORT_PATH);
            }
            
            // Check for absolute paths
            if (value.startsWith("/") || value.matches("^[A-Za-z]:\\\\.*")) {
                return FormValidation.warning("Absolute paths are not recommended. Consider using a path relative to the workspace.");
            }
            
            return FormValidation.ok();
        }

        public FormValidation doCheckCdcReportPath(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.ok("Using default path: " + DEFAULT_CDC_REPORT_PATH);
            }
            
            // Check for absolute paths
            if (value.startsWith("/") || value.matches("^[A-Za-z]:\\\\.*")) {
                return FormValidation.warning("Absolute paths are not recommended. Consider using a path relative to the workspace.");
            }
            
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Publish Questa Formal Results";
        }
    }

    public static class CheckItem {
        private final String name;
        private final int count;
        private String details;
        private final String id;
        private List<String> occurrences;
        private final String type;

        public CheckItem(String name, int count, String details, String type) {
            this.name = name;
            this.count = count;
            this.details = details;
            this.id = name.toLowerCase().replaceAll("\\s+", "-");
            this.occurrences = new ArrayList<>();
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public String getDetails() {
            return details;
        }

        public String getId() {
            return id;
        }

        public String getSanitizedName() {
            return name.replaceAll("[^a-zA-Z0-9]", "_");
        }

        public List<String> getOccurrences() {
            return occurrences;
        }

        public String getType() {
            return type;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public void addOccurrence(String occurrence) {
            this.occurrences.add(occurrence);
        }
    }
}