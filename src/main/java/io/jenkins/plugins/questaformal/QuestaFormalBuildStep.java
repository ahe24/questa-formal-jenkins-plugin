package io.jenkins.plugins.questaformal;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Recorder;
import hudson.tasks.Publisher;
import java.io.File;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class QuestaFormalBuildStep extends Recorder {
    private String lintReportPath;
    private String cdcReportPath;
    
    public QuestaFormalBuildStep() {
        this.lintReportPath = "Lint_Results/lint.rpt";
        this.cdcReportPath = "CDC_result/cdc.rpt";
    }
    
    @DataBoundConstructor
    public QuestaFormalBuildStep(String lintReportPath, String cdcReportPath) {
        this.lintReportPath = lintReportPath;
        this.cdcReportPath = cdcReportPath;
    }

    @DataBoundSetter
    public void setLintReportPath(String lintReportPath) {
        this.lintReportPath = lintReportPath;
    }

    @DataBoundSetter
    public void setCdcReportPath(String cdcReportPath) {
        this.cdcReportPath = cdcReportPath;
    }

    public String getLintReportPath() {
        return lintReportPath != null ? lintReportPath : "Lint_Results/lint.rpt";
    }

    public String getCdcReportPath() {
        return cdcReportPath != null ? cdcReportPath : "CDC_result/cdc.rpt";
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        try {
            FilePath workspace = build.getWorkspace();
            if (workspace != null) {
                FilePath lintFile = workspace.child(getLintReportPath());
                FilePath cdcFile = workspace.child(getCdcReportPath());
                
                listener.getLogger().println("\n=== Questa Formal Analysis Debug Log ===");
                listener.getLogger().println("Workspace: " + workspace.getRemote());
                listener.getLogger().println("Looking for lint report at: " + lintFile.getRemote());
                
                if (lintFile.exists()) {
                    // 파일 전체 내용 읽기
                    String content = lintFile.readToString();
                    listener.getLogger().println("\n=== Lint Report Content ===");
                    
                    // Check Summary 섹션 찾기
                    int summaryStart = content.indexOf("Section 1 : Check Summary");
                    int summaryEnd = content.indexOf("Section 2");
                    
                    if (summaryStart != -1 && summaryEnd != -1) {
                        String summarySection = content.substring(summaryStart, summaryEnd);
                        listener.getLogger().println("\n=== Check Summary Section ===");
                        listener.getLogger().println(summarySection);
                        
                        // Create result object with detailed logging
                        listener.getLogger().println("\n=== Parsing Results ===");
                        QuestaFormalResult result = new QuestaFormalResult(
                            new File(lintFile.getRemote()),
                            cdcFile.exists() ? new File(cdcFile.getRemote()) : null,
                            listener
                        );
                        
                        // Log detailed summary
                        listener.getLogger().println("\n=== Parsed Summary ===");
                        listener.getLogger().println("Error count: " + result.getErrorCount());
                        listener.getLogger().println("Warning count: " + result.getWarningCount());
                        listener.getLogger().println("Info count: " + result.getInfoCount());
                        
                        // Log individual items
                        listener.getLogger().println("\n=== Detailed Items ===");
                        for (QuestaFormalResult.LintSummaryItem item : result.getLintSummary()) {
                            listener.getLogger().println(String.format("%s - %s: %d", 
                                item.getCategory(), item.getName(), item.getCount()));
                        }
                        
                        build.addAction(result);
                        listener.getLogger().println("\n=== Processing Complete ===");
                        return true;
                    } else {
                        listener.error("Could not find Check Summary section in lint report");
                        return false;
                    }
                } else {
                    listener.error("Lint report file not found at: " + getLintReportPath());
                    listener.error("Current directory contents:");
                    for (FilePath child : workspace.list()) {
                        listener.error("  " + child.getName());
                    }
                    return false;
                }
            }
            listener.error("Workspace is null");
            return false;
        } catch (Exception e) {
            e.printStackTrace(listener.error("Error processing Questa Formal results"));
            return false;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return "Add Questa Formal Results";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
