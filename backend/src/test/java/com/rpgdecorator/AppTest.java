package com.rpgdecorator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void mainPrintsBanner() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            App.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        assertEquals("RPG Decorator", captured.toString(StandardCharsets.UTF_8).strip());
    }
}
