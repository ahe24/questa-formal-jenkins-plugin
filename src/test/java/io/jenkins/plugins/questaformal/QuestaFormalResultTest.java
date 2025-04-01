package io.jenkins.plugins.questaformal;

import hudson.model.BuildListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class QuestaFormalResultTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private BuildListener listener;
    private PrintStream logger;
    private File testLintFile;

    @Before
    public void setUp() {
        // Mock BuildListener 설정
        listener = Mockito.mock(BuildListener.class);
        logger = Mockito.mock(PrintStream.class);
        Mockito.when(listener.getLogger()).thenReturn(logger);
        
        // 테스트 lint.rpt 파일 경로 설정
        testLintFile = new File(getClass().getResource("/test-lint.rpt").getFile());
    }

    @Test
    public void testLintResults() {
        String testLintPath = testLintFile.getAbsolutePath();
        QuestaFormalResult result = new QuestaFormalResult(testLintPath, null, listener);
        
        // 기본 정보 검증
        assertNotNull("Design name should not be null", result.getLintDesign());
        assertNotNull("Timestamp should not be null", result.getLintTimestamp());
        assertTrue("Quality score should be >= 0", result.getQualityScore() >= 0);
        
        // 카운트 검증
        assertEquals("Should have 7 errors", 7, result.getLintErrorCount());
        assertEquals("Should have 12 warnings", 12, result.getLintWarningCount());
        assertEquals("Should have 51 info items", 51, result.getLintInfoCount());
        
        // Check Details 검증
        List<QuestaFormalResult.CheckItem> checks = result.getAllLintChecks();
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
