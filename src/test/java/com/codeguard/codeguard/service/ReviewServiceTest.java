package com.codeguard.codeguard.service;

import com.codeguard.codeguard.model.Finding;
import com.codeguard.codeguard.model.ReviewResponse;
import com.codeguard.codeguard.rule.CodeReviewRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private AiReviewService aiReviewService;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {

        aiReviewService =
                mock(AiReviewService.class);

        /*
         * We use no static rules in these tests
         * so we can test the AI findings and
         * scoring behavior directly.
         */
        List<CodeReviewRule> rules =
                List.of();

        reviewService =
                new ReviewService(
                        rules,
                        aiReviewService
                );
    }

    @Test
    void shouldReturnPerfectScoreWhenNoFindingsExist() {

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of()
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "public class Test {}"
                );

        assertEquals(
                10,
                response.getScore()
        );

        assertTrue(
                response.getFindings()
                        .isEmpty()
        );
    }

    @Test
    void shouldDeductThreePointsForHighFinding() {

        Finding finding =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password logging",
                        "Password is exposed.",
                        "Remove password logging.",
                        "LoginService.java",
                        12
                );

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of(
                        finding
                )
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "test code"
                );

        assertEquals(
                7,
                response.getScore()
        );
    }

    @Test
    void shouldCalculateHighAndMediumScoreCorrectly() {

        Finding high =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password logging",
                        "Sensitive data exposed.",
                        "Remove logging.",
                        "LoginService.java",
                        12
                );

        Finding medium =
                new Finding(
                        "MEDIUM",
                        "Error Handling",
                        "Empty catch block",
                        "Exception swallowed.",
                        "Handle exception.",
                        "LoginService.java",
                        7
                );

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of(
                        high,
                        medium
                )
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "test code"
                );

        /*
         * 10 - 3 - 1.5 = 5.5
         *
         * Current integer API rounds this
         * to 6.
         */
        assertEquals(
                6,
                response.getScore()
        );

        assertEquals(
                2,
                response.getFindings()
                        .size()
        );
    }

    @Test
    void shouldNeverReturnNegativeScore() {

        Finding critical1 =
                criticalFinding(
                        "Critical 1",
                        1
                );

        Finding critical2 =
                criticalFinding(
                        "Critical 2",
                        2
                );

        Finding critical3 =
                criticalFinding(
                        "Critical 3",
                        3
                );

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of(
                        critical1,
                        critical2,
                        critical3
                )
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "bad code"
                );

        assertEquals(
                0,
                response.getScore()
        );
    }

    @Test
    void shouldRemoveExactDuplicateFindings() {

        Finding first =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password logging",
                        "Sensitive password.",
                        "Remove logging.",
                        "LoginService.java",
                        12
                );

        Finding duplicate =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password Logging",
                        "Different explanation.",
                        "Different suggestion.",
                        "LoginService.java",
                        12
                );

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of(
                        first,
                        duplicate
                )
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "test"
                );

        assertEquals(
                1,
                response.getFindings()
                        .size()
        );

        assertEquals(
                7,
                response.getScore()
        );
    }

    @Test
    void shouldNotMergeSameFindingFromDifferentFiles() {

        Finding first =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password logging",
                        "Issue",
                        "Fix",
                        "LoginService.java",
                        12
                );

        Finding second =
                new Finding(
                        "HIGH",
                        "Security",
                        "Password logging",
                        "Issue",
                        "Fix",
                        "AdminLoginService.java",
                        12
                );

        when(
                aiReviewService.reviewCode(
                        anyString()
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        ReviewResponse response =
                reviewService.reviewCode(
                        "test"
                );

        assertEquals(
                2,
                response.getFindings()
                        .size()
        );

        /*
         * Two HIGH findings:
         *
         * 10 - 3 - 3 = 4
         */
        assertEquals(
                4,
                response.getScore()
        );
    }

    private Finding criticalFinding(
            String title,
            int line) {

        return new Finding(
                "CRITICAL",
                "Security",
                title,
                "Critical problem",
                "Fix immediately",
                "Test.java",
                line
        );
    }
}
