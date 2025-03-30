package io.jenkins.plugins.questaformal;

import hudson.model.Action;
import hudson.model.BuildListener;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestaFormalResult implements Action {
    private final Map<String, Integer> lintResults;
    private final Map<String, Integer> cdcResults;
    private final File lintReportFile;
    private final File cdcReportFile;
    private final BuildListener listener; // Add this field

    public QuestaFormalResult(File lintFile, File cdcFile, BuildListener listener) {
        this.lintReportFile = lintFile;
        this.cdcReportFile = cdcFile;
        this.listener = listener;
        this.lintResults = lintFile != null ? parseLintReport(lintFile) : createEmptyLintResults();
        this.cdcResults = cdcFile != null ? parseCDCReport(cdcFile) : createEmptyCDCResults();
    }

    private Map<String, Integer> createEmptyLintResults() {
        Map<String, Integer> results = new HashMap<>();
        results.put("errors", 0);
        results.put("warnings", 0);
        results.put("info", 0);
        return results;
    }

    private Map<String, Integer> createEmptyCDCResults() {
        Map<String, Integer> results = new HashMap<>();
        results.put("violations", 0);
        results.put("cautions", 0);
        return results;
    }

    private Map<String, Integer> parseLintReport(File lintFile) {
        Map<String, Integer> results = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(lintFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("| Error (")) {
                    results.put("errors", extractNumber(line));
                } else if (line.contains("| Warning (")) {
                    results.put("warnings", extractNumber(line));
                } else if (line.contains("| Info (")) {
                    results.put("info", extractNumber(line));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return createEmptyLintResults();
        }
        return results;
    }

    private Map<String, Integer> parseCDCReport(File cdcFile) {
        Map<String, Integer> results = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(cdcFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Violations (")) {
                    results.put("violations", extractNumber(line));
                } else if (line.contains("Cautions (")) {
                    results.put("cautions", extractNumber(line));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return createEmptyCDCResults();
        }
        return results;
    }

    private int extractNumber(String line) {
        try {
            return Integer.parseInt(line.replaceAll(".*\\((\\d+)\\).*", "$1"));
        } catch (Exception e) {
            return 0;
        }
    }

    public String getDetails(String type) {
        try {
            if (lintReportFile == null) return "No details available";
            String content = new String(java.nio.file.Files.readAllBytes(lintReportFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            // 우선 Section 2 : Check Details 영역을 추출
            int sec2Start = content.indexOf("Section 2 : Check Details");
            if (sec2Start == -1) return "Check Details section not found";
            int sec3Start = content.indexOf("Section 3", sec2Start);
            if (sec3Start == -1) sec3Start = content.length();
            String detailsSection = content.substring(sec2Start, sec3Start);
            
            // 어떤 카테고리인지 결정
            String marker;
            switch (type) {
                case "lint-errors":
                    marker = "Error (";
                    break;
                case "lint-warnings":
                    marker = "Warning (";
                    break;
                case "lint-info":
                    marker = "Info (";
                    break;
                default:
                    return "No details available";
            }
            
            // 세부 Details 추출: marker가 나타난 이후부터, 구분선(-------)까지 수집
            StringBuilder result = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(detailsSection));
            String line;
            boolean capture = false;
            while ((line = reader.readLine()) != null) {
                // marker가 나오면 캡처 시작
                if (line.contains(marker)) {
                    capture = true;
                }
                if (capture) {
                    result.append(line).append("\n");
                    // 구분선이 나면 캡처 종료 (단, 현재 카테고리 구분선)
                    if (line.trim().matches("[-]+")) {
                        capture = false;
                    }
                }
            }
            return result.toString();
        } catch (IOException e) {
            return "Error reading details: " + e.getMessage();
        }
    }

    private String extractSection(File file, String startMarker, String endMarker) throws IOException {
        StringBuilder section = new StringBuilder();
        boolean inSection = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(startMarker)) {
                    inSection = true;
                    continue;
                }
                if (line.contains(endMarker)) {
                    break;
                }
                if (inSection && !line.trim().isEmpty()) {
                    section.append(line).append("\n");
                }
            }
        }
        return section.toString();
    }

    public Map<String, Integer> getLintResults() {
        return lintResults;
    }

    public Map<String, Integer> getCdcResults() {
        return cdcResults;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/questa-formal-result/images/chart.png";
    }

    @Override
    public String getDisplayName() {
        return "Questa Formal Results";
    }

    @Override
    public String getUrlName() {
        return "questa-formal";  // URL 경로를 이전 값으로 복원
    }

    public List<CheckItem> getLintErrorChecks() {
        return getLintChecks("Error");
    }

    public List<CheckItem> getLintWarningChecks() {
        return getLintChecks("Warning");
    }

    public List<CheckItem> getLintInfoChecks() {
        return getLintChecks("Info");
    }

    private List<CheckItem> getLintChecks(String type) {
        List<CheckItem> result = new ArrayList<>();
        try {
            String content = extractSection(lintReportFile, "| " + type + " (", "----------------");
            Pattern checkPattern = Pattern.compile("Check: (.*?)\\[Category:.*?\\] \\((\\d+)\\)(.*?)", Pattern.DOTALL);
            Matcher checkMatcher = checkPattern.matcher(content);

            while (checkMatcher.find()) {
                String name = checkMatcher.group(1).trim();
                int count = Integer.parseInt(checkMatcher.group(2).trim());
                String details = checkMatcher.group(3).trim();
                result.add(new CheckItem(name, count, details));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<ViolationItem> getCdcViolations() {
        return getCdcChecks("Violations");
    }

    public List<ViolationItem> getCdcCautions() {
        return getCdcChecks("Cautions");
    }

    private List<ViolationItem> getCdcChecks(String type) {
        List<ViolationItem> result = new ArrayList<>();
        try {
            String content = extractSection(cdcReportFile, type + " (", "Evaluations (");
            Pattern checkPattern = Pattern.compile("(.*?)\\((\\d+)\\)(.*?)", Pattern.DOTALL);
            Matcher checkMatcher = checkPattern.matcher(content);

            while (checkMatcher.find()) {
                String name = checkMatcher.group(1).trim();
                int count = Integer.parseInt(checkMatcher.group(2).trim());
                String details = checkMatcher.group(3).trim();
                result.add(new ViolationItem(name, count, details));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public String getLintDesign() {
        try (BufferedReader reader = new BufferedReader(new FileReader(lintReportFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Design               :")) {
                    return line.split(":")[1].trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    public String getLintTimestamp() {
        try (BufferedReader reader = new BufferedReader(new FileReader(lintReportFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Timestamp            :")) {
                    String[] mainParts = line.split(":", 2);
                    if (mainParts.length < 2) continue;
                    
                    String timestamp = mainParts[1].trim();
                    String[] parts = timestamp.split("\\s+");
                    
                    if (parts.length >= 5) {
                        // Wed Mar 26 14:24:27 2025 -> 2025 Mar 26, 14:24
                        String[] timeParts = parts[3].split(":");
                        return String.format("%s-%s-%s,  %s:%s",
                            parts[4],      // Year (2025)
                            parts[1],      // Month (Mar)
                            parts[2],      // Day (26)
                            timeParts[0],  // Hour (14)
                            timeParts[1]   // Minute (24)
                        );
                    }
                    return timestamp;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error parsing timestamp: " + e.getMessage());
        }
        return "N/A";
    }

    public String getQualityScore() {
        try (BufferedReader reader = new BufferedReader(new FileReader(lintReportFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Design Quality Score :")) {
                    return line.split(":")[1].trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    public int getQualityScoreRotation() {
        String score = getQualityScore();
        if (score.equals("N/A")) return 0;
        
        try {
            double percentage = Double.parseDouble(score.replace("%", ""));
            // 반원 게이지를 위한 SVG viewBox 좌표계에 맞춰 각도 계산
            // 0% = -90도(왼쪽), 50% = 0도(중앙), 100% = 90도(오른쪽)
            return (int)(-90 + (180 * percentage / 100.0));
        } catch (NumberFormatException e) {
            return -90; // 0% 위치
        }
    }

    public String getQualityLevel() {
        String score = getQualityScore();
        if (score.equals("N/A")) return "poor";
        
        try {
            double percentage = Double.parseDouble(score.replace("%", ""));
            if (percentage >= 90) return "good";
            if (percentage >= 50) return "normal";
            return "poor";
        } catch (NumberFormatException e) {
            return "poor";
        }
    }

    public float getQualityScorePercentage() {
        String score = getQualityScore();
        if (score.equals("N/A")) return 0;
        
        try {
            return Float.parseFloat(score.replace("%", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getErrorCount() {
        return lintResults.getOrDefault("errors", 0);
    }

    public int getWarningCount() {
        return lintResults.getOrDefault("warnings", 0);
    }

    public int getInfoCount() {
        return lintResults.getOrDefault("info", 0);
    }

    public String getErrorDetails() {
        return getDetails("lint-errors");
    }

    public String getWarningDetails() {
        return getDetails("lint-warnings");
    }

    public String getInfoDetails() {
        return getDetails("lint-info");
    }

    public static class CheckItem {
        public final String name;
        public final int count;
        public final String details;
        public final String id;

        public CheckItem(String name, int count, String details) {
            this.name = name;
            this.count = count;
            this.details = details;
            this.id = name.toLowerCase().replaceAll("\\s+", "-");
        }
    }

    public static class ViolationItem {
        public final String type;
        public final int count;
        public final String details;
        public final String id;

        public ViolationItem(String type, int count, String details) {
            this.type = type;
            this.count = count;
            this.details = details;
            this.id = type.toLowerCase().replaceAll("\\s+", "-");
        }
    }

    public static class LintSummaryItem {
        private final String category;
        private final String name;
        private final int count;

        public LintSummaryItem(String category, String name, int count) {
            this.category = category;
            this.name = name;
            this.count = count;
        }

        public String getCategory() { return category; }
        public String getName() { return name; }
        public int getCount() { return count; }
    }

    public List<LintSummaryItem> getLintSummary() {
        List<LintSummaryItem> summary = new ArrayList<>();
        if (listener != null) {
            listener.getLogger().println("Reading lint report from: " + lintReportFile.getAbsolutePath());
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(lintReportFile))) {
            String line;
            String currentCategory = null;
            boolean inSummarySection = false;
            while ((line = reader.readLine()) != null) {
                // Skip separator lines (e.g. "-----" or "=====")
                if (line.trim().matches("[-=]+")) continue;
                if (line.contains("Section 1 : Check Summary")) {
                    inSummarySection = true;
                    if (listener != null) {
                        listener.getLogger().println("Found Section 1");
                    }
                    continue;
                }
                if (!inSummarySection) continue;
                if (line.contains("Section 2")) {
                    if (listener != null) {
                        listener.getLogger().println("End of Section 1");
                    }
                    break;
                }
                // Category header, e.g. "| Error (7) |"
                if (line.trim().startsWith("| ") && line.contains(" (")) {
                    currentCategory = line.split("\\(")[0].replace("|", "").trim();
                    if (listener != null) {
                        listener.getLogger().println("Found category: " + currentCategory);
                    }
                    continue;
                }
                // Parse item lines like "  assign_width_underflow : 6"
                if (currentCategory != null && !line.startsWith("|") && line.contains(":")) {
                    String[] parts = line.trim().split("\\s*:\\s*");
                    if (parts.length == 2) {
                        try {
                            String name = parts[0].trim();
                            int count = Integer.parseInt(parts[1].trim());
                            summary.add(new LintSummaryItem(currentCategory, name, count));
                            if (listener != null) {
                                listener.getLogger().println(
                                    String.format("Added item: %s - %s: %d", currentCategory, name, count));
                            }
                        } catch (NumberFormatException e) {
                            if (listener != null) {
                                listener.getLogger().println("Failed to parse count in line: " + line);
                            }
                        }
                    }
                }
            }
            if (listener != null) {
                listener.getLogger().println("Total items parsed: " + summary.size());
                for (LintSummaryItem item : summary) {
                    listener.getLogger().println(
                        String.format("%s: %s (%d)", item.getCategory(), item.getName(), item.getCount()));
                }
            }
        } catch (IOException e) {
            if (listener != null) {
                listener.getLogger().println("Error reading lint report: " + e.getMessage());
                e.printStackTrace(listener.getLogger());
            }
        }
        return summary;
    }
}