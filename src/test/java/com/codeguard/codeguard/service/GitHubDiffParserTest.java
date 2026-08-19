package com.codeguard.codeguard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubDiffParserTest {

    private GitHubDiffParser parser;

    @BeforeEach
    void setUp() {

        parser =
                new GitHubDiffParser();
    }

    @Test
    void shouldConvertPatchToCorrectNewFileLines() {

        String patch = """
                @@ -4,5 +4,6 @@ public class LoginService {
                     public void login(String password) {
                         authenticate(password);
                +        System.out.println(password);
                     }
                 }
                """;

        String result =
                parser.convertToNumberedNewLines(
                        "LoginService.java",
                        patch
                );

        assertTrue(
                result.contains(
                        "FILE: LoginService.java"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 4:     public void login(String password) {"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 6:         System.out.println(password);"
                )
        );
    }

    @Test
    void shouldIgnoreDeletedLines() {

        String patch = """
                @@ -10,3 +10,3 @@
                -System.out.println("old");
                +System.out.println("new");
                 return;
                """;

        String result =
                parser.convertToNumberedNewLines(
                        "Test.java",
                        patch
                );

        assertFalse(
                result.contains(
                        "\"old\""
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 10: System.out.println(\"new\");"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 11: return;"
                )
        );
    }

    @Test
    void shouldSupportMultipleDiffHunks() {

        String patch = """
                @@ -2,2 +2,2 @@
                 first();
                +second();

                @@ -20,2 +21,2 @@
                 foo();
                +bar();
                """;

        String result =
                parser.convertToNumberedNewLines(
                        "Example.java",
                        patch
                );

        assertTrue(
                result.contains(
                        "NEW LINE 2: first();"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 3: second();"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 21: foo();"
                )
        );

        assertTrue(
                result.contains(
                        "NEW LINE 22: bar();"
                )
        );
    }

    @Test
    void shouldReturnEmptyForBlankPatch() {

        String result =
                parser.convertToNumberedNewLines(
                        "Test.java",
                        ""
                );

        assertEquals(
                "",
                result
        );
    }
}