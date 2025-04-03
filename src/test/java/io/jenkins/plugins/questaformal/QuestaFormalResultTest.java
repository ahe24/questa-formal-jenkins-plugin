package io.jenkins.plugins.questaformal;

import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class QuestaFormalResultTest {
    private static final String DEFAULT_CDC_REPORT_PATH = "CDC_result/cdc.rpt";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private BuildListener listener;
    private PrintStream logger;
    private File testLintFile;
    private Run<?, ?> run;
    private FilePath workspace;
    private Launcher launcher;
    private List<Action> actions;

    @Before
    public void setUp() throws Exception {
        // Mock BuildListener 설정
        listener = Mockito.mock(BuildListener.class);
        logger = Mockito.mock(PrintStream.class);
        Mockito.when(listener.getLogger()).thenReturn(logger);
        
        // 테스트 lint.rpt 파일 경로 설정
        testLintFile = new File(getClass().getResource("/test-lint.rpt").getFile());
        
        // Mock Run, FilePath, Launcher 설정
        run = Mockito.mock(Run.class);
        workspace = Mockito.mock(FilePath.class);
        launcher = Mockito.mock(Launcher.class);
        actions = new ArrayList<>();
        
        when(workspace.child(anyString())).thenReturn(workspace);
        when(workspace.exists()).thenReturn(true);
        when(workspace.read()).thenReturn(new FileInputStream(testLintFile));
        
        // Mock Run의 addAction 메소드 설정
        doAnswer(invocation -> {
            Action action = invocation.getArgument(0);
            actions.add(action);
            logger.println("Action added: " + action.getClass().getName());
            return null;
        }).when(run).addAction(any(Action.class));
        
        // Mock Run의 getAllActions 메소드 설정
        when(run.getAllActions()).thenAnswer(invocation -> {
            logger.println("Getting all actions. Count: " + actions.size());
            return new ArrayList<>(actions);
        });
    }

    @Test
    public void testLintResults() throws Exception {
        String testLintPath = testLintFile.getAbsolutePath();
        QuestaFormalResult result = new QuestaFormalResult(testLintPath, DEFAULT_CDC_REPORT_PATH);
        
        // 디버그 모드 활성화
        result.setDebugMode(true);
        
        result.perform(run, workspace, launcher, listener);
        
        // Action이 추가되었는지 확인
        verify(run).addAction(any(QuestaFormalResultAction.class));
        
        // Action의 결과를 검증
        QuestaFormalResultAction action = null;
        List<? extends Action> allActions = run.getAllActions();
        logger.println("Total actions found: " + allActions.size());
        
        for (Action a : allActions) {
            logger.println("Checking action: " + a.getClass().getName());
            if (a instanceof QuestaFormalResultAction) {
                action = (QuestaFormalResultAction) a;
                logger.println("Found QuestaFormalResultAction");
                break;
            }
        }
        
        assertNotNull("Action should not be null", action);
        
        // 기본 정보 검증
        assertNotNull("Design name should not be null", action.getLintDesign());
        assertNotNull("Timestamp should not be null", action.getLintTimestamp());
        assertTrue("Quality score should be >= 0", action.getQualityScore() >= 0);
        
        // 카운트 검증
        assertEquals("Should have 7 errors", 7, action.getLintErrorCount());
        assertEquals("Should have 12 warnings", 12, action.getLintWarningCount());
        assertEquals("Should have 51 info items", 51, action.getLintInfoCount());
        
        // Check Details 검증
        List<QuestaFormalResult.CheckItem> checks = action.getAllLintChecks();
        assertNotNull("Check list should not be null", checks);
        assertFalse("Check list should not be empty", checks.isEmpty());
        
        // 특정 Check 항목 검증
        boolean foundCheck = false;
        for (QuestaFormalResult.CheckItem check : checks) {
            if (check.getName().contains("assign_width_underflow")) {
                foundCheck = true;
                assertEquals("Check count should match", 6, check.getCount());
                assertNotNull("Check details should not be null", check.getDetails());
                break;
            }
        }
        assertTrue("Should find assign_width_underflow check", foundCheck);
    }
}
