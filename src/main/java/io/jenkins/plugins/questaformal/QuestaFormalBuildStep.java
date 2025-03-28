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
                
                if (!lintFile.exists()) {
                    listener.error("Lint report file not found: " + getLintReportPath());
                    return false;
                }
                
                QuestaFormalResult result = new QuestaFormalResult(
                    new File(lintFile.getRemote()),
                    cdcFile.exists() ? new File(cdcFile.getRemote()) : null
                );
                build.addAction(result);
                return true;
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
