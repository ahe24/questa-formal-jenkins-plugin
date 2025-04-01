package io.jenkins.plugins.questaformal;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;

public class QuestaFormalBuildStep extends Builder {
    private static final String DEFAULT_LINT_REPORT_PATH = "Lint_Results/lint.rpt";
    private static final String DEFAULT_CDC_REPORT_PATH = "CDC_result/cdc_detail.rpt";
    
    private final String lintReportPath;
    private final String cdcReportPath;

    @DataBoundConstructor
    public QuestaFormalBuildStep(String lintReportPath, String cdcReportPath) {
        this.lintReportPath = lintReportPath != null && !lintReportPath.trim().isEmpty() 
            ? lintReportPath.trim() 
            : DEFAULT_LINT_REPORT_PATH;
        this.cdcReportPath = cdcReportPath != null && !cdcReportPath.trim().isEmpty() 
            ? cdcReportPath.trim() 
            : DEFAULT_CDC_REPORT_PATH;
    }

    public String getLintReportPath() {
        return lintReportPath;
    }

    public String getCdcReportPath() {
        return cdcReportPath;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        try {
            // Get workspace
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                listener.error("Workspace is null");
                return false;
            }

            // Get report paths
            String effectiveLintPath = lintReportPath != null && !lintReportPath.trim().isEmpty() 
                ? lintReportPath.trim() 
                : DEFAULT_LINT_REPORT_PATH;
            
            String effectiveCdcPath = cdcReportPath != null && !cdcReportPath.trim().isEmpty() 
                ? cdcReportPath.trim() 
                : DEFAULT_CDC_REPORT_PATH;

            // Log workspace and report paths
            listener.getLogger().println("Workspace path: " + workspace.getRemote());
            listener.getLogger().println("Looking for Lint report at: " + workspace.getRemote() + "/" + effectiveLintPath);
            listener.getLogger().println("Looking for CDC report at: " + workspace.getRemote() + "/" + effectiveCdcPath);

            // Get report files
            FilePath lintReport = workspace.child(effectiveLintPath);
            FilePath cdcReport = workspace.child(effectiveCdcPath);

            // Check if files exist
            if (!lintReport.exists()) {
                listener.error("Lint report file not found at: " + lintReport.getRemote());
                return false;
            }

            // Add action to build with direct file paths
            build.addAction(new QuestaFormalResult(lintReport.getRemote(), cdcReport.exists() ? cdcReport.getRemote() : null, listener));

            return true;
        } catch (IOException e) {
            listener.error("IO error: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            return false;
        } catch (InterruptedException e) {
            listener.error("Build was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            listener.error("Unexpected error: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            return false;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
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
            return "Add Questa Formal Results";
        }
    }
}
