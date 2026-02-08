package com.hayden.multiagentide.cli;

import com.agentclientprotocol.model.PermissionOption;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGateAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CliInteractionLoop {

    private final PermissionGateAdapter permissionGateAdapter;
    private final CliOutputWriter outputWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CliInteractionLoop(PermissionGateAdapter permissionGateAdapter, CliOutputWriter outputWriter) {
        this.permissionGateAdapter = permissionGateAdapter;
        this.outputWriter = outputWriter;
    }

    public void start(BufferedReader reader) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> runLoop(reader), "cli-interaction-loop");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
    }

    private void runLoop(BufferedReader reader) {
        while (running.get()) {
            handlePermissionRequests(reader);
            handleInterruptRequests(reader);
            sleepQuietly(1000);
        }
    }

    private void handlePermissionRequests(BufferedReader reader) {
        List<IPermissionGate.PendingPermissionRequest> pending =
                new ArrayList<>(permissionGateAdapter.pendingPermissionRequests());
        for (IPermissionGate.PendingPermissionRequest request : pending) {
            outputWriter.println("Permission requested for tool call " + request.getToolCallId());
            List<PermissionOption> permissions = request.getPermissions();
            for (int i = 0; i < permissions.size(); i++) {
                PermissionOption option = permissions.get(i);
                outputWriter.println("  " + (i + 1) + ". " + option.getName() + " (" + option.getKind() + ")");
            }
            outputWriter.prompt("Select option [1.." + permissions.size() + "], optionType, or 'cancel': ");
            String input = readLine(reader);
            if (input == null) {
                return;
            }
            String trimmed = input.trim();
            if (trimmed.isBlank()) {
                permissionGateAdapter.resolveSelected(request.getRequestId(), permissions.isEmpty() ? null : permissions.get(0));
                continue;
            }
            if ("cancel".equalsIgnoreCase(trimmed)) {
                permissionGateAdapter.resolveCancelled(request.getRequestId());
                continue;
            }
            try {
                int index = Integer.parseInt(trimmed);
                if (index >= 1 && index <= permissions.size()) {
                    PermissionOption selected = permissions.get(index - 1);
                    permissionGateAdapter.resolveSelected(request.getRequestId(), selected);
                    continue;
                }
            } catch (NumberFormatException ignored) {
            }
            permissionGateAdapter.resolveSelected(request.getRequestId(), trimmed);
        }
    }

    private void handleInterruptRequests(BufferedReader reader) {
        List<IPermissionGate.PendingInterruptRequest> pending =
                new ArrayList<>(permissionGateAdapter.pendingInterruptRequests());
        for (IPermissionGate.PendingInterruptRequest request : pending) {
            outputWriter.println("Interrupt requested: " + request.getType() + " (" + request.getInterruptId() + ")");
            if (request.getReason() != null && !request.getReason().isBlank()) {
                outputWriter.println("Reason: " + request.getReason());
            }
            outputWriter.prompt("Provide input (or type 'approve'): ");
            String input = readLine(reader);
            if (input == null) {
                return;
            }
            String trimmed = input.trim();
            String resolutionType;
            String resolutionNotes;
            if (trimmed.isBlank()) {
                resolutionType = "resolved";
                resolutionNotes = "";
            } else if ("approve".equalsIgnoreCase(trimmed) || "approved".equalsIgnoreCase(trimmed)) {
                resolutionType = "approved";
                resolutionNotes = "approved";
            } else {
                resolutionType = "feedback";
                resolutionNotes = trimmed;
            }
            permissionGateAdapter.resolveInterrupt(
                    request.getInterruptId(),
                    resolutionType,
                    resolutionNotes,
                    null
            );
        }
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            outputWriter.println("Error reading input: " + e.getMessage());
            return null;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
