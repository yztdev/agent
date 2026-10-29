package dev.terminalagent.client;

import java.io.Console;
import java.util.Locale;

/** Ask separately for each question; closing the terminal cancels the request. */
final class OptimizationChoice {
    static Boolean ask(Console console) {
        while (true) {
            String answer = console.readLine("是否优化这个问题？[y/N]（优化会额外调用模型） ");
            if (answer == null) return null;
            Boolean choice = parse(answer);
            if (choice != null) return choice;
            System.out.println("请输入 y 或 n。");
        }
    }

    static Boolean parse(String answer) {
        return switch (answer.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "是" -> true;
            case "", "n", "no", "否" -> false;
            default -> null;
        };
    }

    private OptimizationChoice() {}
}
