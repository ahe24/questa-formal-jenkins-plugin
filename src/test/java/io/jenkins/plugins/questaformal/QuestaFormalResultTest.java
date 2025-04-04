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
import hudson.model.TaskListener;
import static io.jenkins.plugins.questaformal.QuestaFormalResult.DEFAULT_CDC_REPORT_PATH;
import java.io.PrintWriter;

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

    @Test
    public void testFilePathSanitization() throws Exception {
        // 테스트 파일 경로 설정
        String testLintPath = "Lint_Results/lint.rpt";
        FilePath workspace = new FilePath(new File("."));
        FilePath reportFile = workspace.child(testLintPath);
        
        // QuestaFormalResult 인스턴스 생성
        QuestaFormalResult result = new QuestaFormalResult(testLintPath, "CDC_result/cdc.rpt");
        result.setDebugMode(true);  // 디버그 모드 활성화
        
        // TaskListener 목업 생성
        TaskListener listener = new TaskListener() {
            private final PrintStream logger = System.out;
            private final PrintWriter writer = new PrintWriter(logger);
            
            @Override
            public PrintWriter error(String msg) {
                logger.println("ERROR: " + msg);
                return writer;
            }
            
            @Override
            public PrintWriter error(String format, Object... args) {
                logger.printf("ERROR: " + format + "%n", args);
                return writer;
            }
            
            @Override
            public PrintStream getLogger() {
                return logger;
            }
        };
        
        try {
            // 파일 처리 테스트 - Run 객체 없이 직접 파싱
            reportFile = workspace.child(testLintPath);
            result.parseLintReport(reportFile, listener);
            
            // QuestaFormalResultAction 생성 및 검증
            QuestaFormalResultAction action = new QuestaFormalResultAction(
                "test_design", "test_timestamp", 98.7,
                result.getLintErrors().size(),
                result.getLintWarnings().size(),
                result.getLintInfo().size(),
                result.getAllLintChecks(),
                true
            );
            
            // index.jelly에서 사용되는 메소드들을 통해 값 검증
            List<QuestaFormalResult.CheckItem> allChecks = action.getAllLintChecks();
            assertFalse("Should have found some checks", allChecks.isEmpty());
            
            // 각 체크 항목의 발생 내용 검증
            for (QuestaFormalResult.CheckItem check : allChecks) {
                System.out.println("\nCheck: " + check.getName());
                System.out.println("Type: " + check.getType());
                System.out.println("Count: " + check.getCount());
                System.out.println("Details: " + check.getDetails());
                
                // index.jelly에서 사용되는 getOccurrences() 메소드를 통해 발생 항목 검증
                List<String> occurrences = check.getOccurrences();
                assertNotNull("Occurrences should not be null", occurrences);
                
                for (String occurrence : occurrences) {
                    System.out.println("  Occurrence: " + occurrence);
                    
                    // 워크스페이스 경로가 제거되었는지 확인
                    assertFalse("Workspace path should be removed from occurrence", 
                        occurrence.contains("/var/lib/jenkins/workspace/"));
                    
                    // PROJ_ROOT로 대체되었는지 확인
                    if (occurrence.contains("File:")) {
                        assertTrue("Path should contain PROJ_ROOT in occurrence", 
                            occurrence.contains("PROJ_ROOT/"));
                            
                            // 파일 경로 형식 검증
                            assertTrue("Occurrence should have proper file path format",
                                occurrence.matches(".*\\(File: PROJ_ROOT/.*\\).*"));
                    }
                    
                    // 라인 번호 형식 검증
                    if (occurrence.contains("Line:")) {
                        assertTrue("Occurrence should have proper line number format",
                            occurrence.matches(".*Line: '[0-9]+'.*"));
                    }
                }
            }
            
            // index.jelly에서 사용되는 변수들이 올바르게 설정되었는지 검증
            assertNotNull("Design name should not be null", action.getLintDesign());
            assertNotNull("Timestamp should not be null", action.getLintTimestamp());
            assertTrue("Quality score should be >= 0", action.getQualityScore() >= 0);
            
            // 각 카테고리의 체크 항목이 존재하는지 확인
            List<QuestaFormalResult.CheckItem> errors = action.getLintErrors();
            List<QuestaFormalResult.CheckItem> warnings = action.getLintWarnings();
            List<QuestaFormalResult.CheckItem> infos = action.getLintInfo();
            
            assertFalse("Should have some errors", errors.isEmpty());
            assertFalse("Should have some warnings", warnings.isEmpty());
            assertFalse("Should have some info items", infos.isEmpty());
            
            // 각 카테고리의 체크 항목에서 파일 경로 치환 검증
            verifyFilePathSanitization(errors, "errors");
            verifyFilePathSanitization(warnings, "warnings");
            verifyFilePathSanitization(infos, "info");
            
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    private void verifyFilePathSanitization(List<QuestaFormalResult.CheckItem> checks, String category) {
        System.out.println("\nVerifying file path sanitization for " + category + ":");
        
        for (QuestaFormalResult.CheckItem check : checks) {
            System.out.println("\nCheck: " + check.getName());
            System.out.println("Type: " + check.getType());
            
            // 체크 항목의 상세 내용에서 파일 경로 검증
            String details = check.getDetails();
            if (details != null && details.contains("File:")) {
                assertFalse("Workspace path should be removed from details in " + category,
                    details.contains("/var/lib/jenkins/workspace/"));
                assertTrue("Details should contain PROJ_ROOT in " + category,
                    details.contains("PROJ_ROOT/"));
            }
            
            // 발생 항목에서 파일 경로 검증
            List<String> occurrences = check.getOccurrences();
            assertNotNull("Occurrences should not be null for " + category, occurrences);
            
            for (String occurrence : occurrences) {
                System.out.println("  Occurrence: " + occurrence);
                
                // 워크스페이스 경로가 제거되었는지 확인
                assertFalse("Workspace path should be removed from occurrence in " + category, 
                    occurrence.contains("/var/lib/jenkins/workspace/"));
                
                // PROJ_ROOT로 대체되었는지 확인
                if (occurrence.contains("File:")) {
                    assertTrue("Path should contain PROJ_ROOT in occurrence in " + category, 
                        occurrence.contains("PROJ_ROOT/"));
                        
                    // 파일 경로 형식 검증
                    assertTrue("Occurrence should have proper file path format in " + category,
                        occurrence.matches(".*\\(File: PROJ_ROOT/.*\\).*"));
                }
            }
        }
    }
}
