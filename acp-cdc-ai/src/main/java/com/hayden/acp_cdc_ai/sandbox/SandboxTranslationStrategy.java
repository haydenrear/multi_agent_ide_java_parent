package com.hayden.acp_cdc_ai.sandbox;


import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

public interface SandboxTranslationStrategy {

    Logger log = LoggerFactory.getLogger(SandboxTranslationStrategy.class);

    static List<String> parseFromAcpArgsCodex(List<String> acpArgs, String modelName) {
        return  parseFromAcpArgs(acpArgs, modelName, (k, v) -> {
            List<String> a = new ArrayList<>();
            a.add("-c");
            a.add("%s=%s".formatted(k, v));
            return a;
        });
    }

    static List<String> parseFromAcpArgsClaude(List<String> acpArgs, String modelName) {
        return parseFromAcpArgs(acpArgs, modelName, (k, v) -> {
            List<String> a = new ArrayList<>();
            a.add("--" + k);
            a.add(v);
            return a;
        });
    }

    static List<String> parseFromAcpArgs(List<String> acpArgs, String modelName, BiFunction<String, String, List<String>> argParser) {
        List<String> args = new ArrayList<>();
        if (acpArgs.isEmpty()) {
            return new ArrayList<>();
        }

        if (acpArgs.size() % 2 != 0) {
            log.error("Acp args not in valid format.");
            return new ArrayList<>();
        }

        for (int i = 0; i < acpArgs.size() - 1; i += 2) {
            if (!acpArgs.get(i).startsWith("--")) {
                log.error("Arg was not in valid format: {}.", acpArgs.get(i));
                continue;
            }

            String arg = acpArgs.get(i).replaceFirst("--", "");

            if (Objects.equals(arg, "model") && !Objects.equals(modelName, "DEFAULT")) {
                args.addAll(argParser.apply("model", modelName));
                continue;
            }

            args.addAll(argParser.apply(arg, acpArgs.get(i + 1)));
        }

        return args;
    }

    String providerKey();

    SandboxTranslation translate(RequestContext context, List<String> args, String modelName);

    default SandboxTranslation translate(RequestContext context, List<String> args) {
        return translate(context, args, "DEFAULT");
    }
}
