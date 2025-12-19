package io.corrlang.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Starts a process for the CorrLang core service in the background.
 */
public class ProcessStarter {

    private static final String CORE_SERVICE_MAIN = "io.corrlang.service.CoreServiceMain";

    public static void startCoreServiceProcess(File corrlangHome) {
        String jvm;
        if (System.getenv("CORRLANG_JAVA") != null) {
            jvm =  System.getenv("CORRLANG_JAVA") + "/bin/java";
        } else if (System.getenv("JAVA_HOME") != null) {
            jvm =  System.getenv("JAVA_HOME") + "/bin/java";
        } else {
            jvm = "java";
        }

        File libDir = new File(corrlangHome, "lib");
        List<String> classpath = new ArrayList<>();
        if (libDir.exists() && libDir.isDirectory()) {
            File[] files = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (files != null) {
                for (File file : files) {
                    classpath.add(file.getAbsolutePath());
                }
            }
        }

        StringBuilder classpathString = new StringBuilder();
        ListIterator<String> cpIterator = classpath.listIterator();
        while (cpIterator.hasNext()) {
            classpathString.append(cpIterator.next());
            if (cpIterator.hasNext()) {
                classpathString.append(File.pathSeparator);
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                jvm,
                "-cp",
                classpathString.toString(),
                CORE_SERVICE_MAIN,
                new File(corrlangHome, "config.toml").getAbsolutePath()
        );
        // processBuilder.inheritIO();
        try {
            processBuilder.start();
            System.out.println("INFO: Started CorrLang core service process");
        } catch (Exception e) {
            throw new RuntimeException("ERROR: Failed to start CorrLang core service process", e);
        }
    }


}
