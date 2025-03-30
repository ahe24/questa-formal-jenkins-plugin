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
    public void testGetLintSummary() {
        QuestaFormalResult result = new QuestaFormalResult(testLintFile, null, listener);
        List<QuestaFormalResult.LintSummaryItem> summary = result.getLintSummary();

        // 파일 파싱 내용 출력
        System.out.println("\n=== Lint Summary Test Results ===");
        System.out.println("File path: " + testLintFile.getAbsolutePath());
        System.out.println("Summary items count: " + summary.size());
        
        // 각 항목 출력
        for (QuestaFormalResult.LintSummaryItem item : summary) {
            System.out.println(String.format("%s - %s: %d", 
                item.getCategory(), item.getName(), item.getCount()));
        }

        // 로그 출력 확인
        Mockito.verify(logger).println(Mockito.contains("Reading lint report from:"));
        
        // 상세 결과 검증
        assertNotNull("Summary should not be null", summary);
        assertFalse("Summary should not be empty", summary.isEmpty());
        System.out.println("\nExpected items count by category:");
        
        // 카테고리별 항목 수 검증
        int errorCount = 0, warningCount = 0, infoCount = 0;
        for (QuestaFormalResult.LintSummaryItem item : summary) {
            switch (item.getCategory().trim()) {
                case "Error": errorCount++; break;
                case "Warning": warningCount++; break;
                case "Info": infoCount++; break;
            }
        }
        
        System.out.println("Error items: " + errorCount);
        System.out.println("Warning items: " + warningCount);
        System.out.println("Info items: " + infoCount);
        
        // 구체적인 항목 검증
        assertEquals("Should have 2 error items", 2, errorCount);
        assertEquals("Should have 2 warning items", 2, warningCount);
        assertEquals("Should have 2 info items", 2, infoCount);
        
        // 특정 항목 검증
        boolean foundError = false;
        for (QuestaFormalResult.LintSummaryItem item : summary) {
            if ("Error".equals(item.getCategory()) && 
                "assign_width_underflow".equals(item.getName()) && 
                item.getCount() == 6) {
                foundError = true;
                break;
            }
        }
        assertTrue("Should find assign_width_underflow error with count 6", foundError);
    }
}
