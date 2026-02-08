package com.hayden.multiagentide.cli;

import java.io.PrintStream;

public class CliOutputWriter {

    private final PrintStream out;
    private final Object lock = new Object();

    public CliOutputWriter() {
        this(System.out);
    }

    public CliOutputWriter(PrintStream out) {
        this.out = out;
    }

    public void println(String message) {
        synchronized (lock) {
            out.println(message);
            out.flush();
        }
    }

    public void print(String message) {
        synchronized (lock) {
            out.print(message);
            out.flush();
        }
    }

    public void printf(String format, Object... args) {
        synchronized (lock) {
            out.printf(format, args);
            out.flush();
        }
    }

    public void prompt(String message) {
        print(message);
    }
}
