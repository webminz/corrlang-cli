package io.corrlang.cli;

import io.grpc.StatusRuntimeException;
import org.apache.commons.cli.*;
import org.apache.commons.cli.help.HelpFormatter;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Runner {

    private static final String VERSION = "1.0-SNAPSHOT";

    private static final String LOGO =
                    "╔═╗┌─┐┬─┐┬─┐╦  ┌─┐┌┐┌┌─┐\n" +
                    "║  │ │├┬┘├┬┘║  ├─┤││││ ┬\n" +
                    "╚═╝└─┘┴└─┴└─╩═╝┴ ┴┘└┘└─┘";
    public static final String DEFAULT_CORRLANG_INSTALL_DIRNAME = ".corrlang";
    public static final String INFO_CMD = "info";
    public static final String INSTALL_CMD = "install";
    public static final String STATUS_CMD = "status";
    public static final String UP_CMD = "up";
    public static final String DOWN_CMD = "down";
    public static final String LIST_CMD = "list";
    public static final String APPLY_CMD = "apply";
    public static final String GET_CMD = "get";


    private static Options makeOptions() {
        System.setProperty("org.apache.commons.cli.help.width", "120");
        System.setProperty("io.netty.noUnsafe", "true");

        Options result = new Options();
        result.addOption("f", "file", true, "The path of a 'spec(ification)' file that shall be applied");
        result.addOption("t", "tech", true, "The name of the 'techspace' that shall be used for the operation");
        result.addOption("p", "project", true, "The name of the 'project' where the referenced element lives");
        result.addOption("e", "endpoint", true, "The name of the 'endpoint' that shall be accessed/modified");
        result.addOption("c", "correspondence", true, "The name of the 'correspondence' that shall be accessed/modified");
        result.addOption("v", "view", true, "The name of the 'view' that shall be accessed/modified");

        OptionGroup directModifications = new OptionGroup();

        directModifications.addOption(Option.builder()
                .longOpt("add-schema-file")
                .desc("Expects the file path of a schema and registers this schema for the specified endpoint")
                .type(Path.class)
                .hasArg()
                .get());
        directModifications.addOption(Option.builder()
                .longOpt("add-schema-url")
                .hasArg()
                .type(URL.class)
                .desc("Expects a URL of a schema and registers this schema for the specified endpoint")
                .get());
        directModifications.addOption(
                Option.builder()
                        .longOpt("add-data")
                        .hasArgs()
                        .type(Path.class)
                        .desc("Expects a file path and loads the file contents for a dataset endpoint")
                        .get());
        directModifications.addOption(
                Option.builder()
                        .longOpt("set-service-url")
                        .hasArg()
                        .type(URL.class)
                        .desc("Sets the URL of the specified endpoint to the given value")
                        .get()
        );
        directModifications.addOption(
                Option.builder()
                        .longOpt("set-service-socket")
                        .hasArg()
                        .type(String.class)
                        .desc("Sets the socket address (format: 'ADDRESS:PORT') of the specified endpoint to the given value")
                        .get()
        );

        result.addOptionGroup(directModifications);


        result.addOption("h", "help", false, "Display this help window.");


        result.addOption(CORRLANG_PORT);
        result.addOption("J", "java", false, "Specifies the path of the JVM that" +
                " shall be used to run the core-service. If not specified it will use the environment variable CORRLANG_JAVA," +
                " then it will consult JAVA_HOME, and a last resort it will check if there is `java` on the PATH.");

        result.addOption(CORRLANG_HOME);

        result.addOption("V", "version", false, "Specifies the CorrLang version that shall be installed. " +
                "If not specified, the latest stable version will be installed.");
        return result;
    }


    public static final String ENV_CORRLANG_PORT = "CORRLANG_PORT";
    private static Option CORRLANG_PORT = Option.builder()
            .option("P")
            .longOpt("port")
            .hasArg()
            .type(Integer.class)
            .desc("The TCP port number of the CorrLang core-service for IPC. If not specified" +
                    " it will look for an environment variable " + ENV_CORRLANG_PORT + ", afterwards for a PORT file" +
                    " in the CorrLang installation directory and then will fall back to the default 6969.")
            .get();

    public static final String ENV_CORRLANG_HOME = "CORRLANG_HOME";
    private static Option CORRLANG_HOME = Option.builder()
            .option("H")
            .longOpt("home")
            .hasArg()
            .type(Path.class)
            .desc("Uses the specified CorrLang " +
                    "installation directory. If not specified, the environment variable " + ENV_CORRLANG_HOME + " is used. And if " +
                    "the latter was not specified either, the default directory is 'USER_HOME/.corrlang/'.")
            .get();



    private static Path getCorrLangHome(CommandLine line) throws ParseException {
        return line.getParsedOptionValue(CORRLANG_HOME, () -> {
            if (System.getenv().containsKey(ENV_CORRLANG_HOME)) {
                return Path.of(System.getenv(ENV_CORRLANG_HOME));
            } else {
                return Path.of(System.getProperty("user.home"), DEFAULT_CORRLANG_INSTALL_DIRNAME);
            }
        });
    }

    private static int getCorrLangPort(CommandLine line, Path corrLangHome) throws ParseException {
        return line.getParsedOptionValue(CORRLANG_PORT, () -> {
            if (System.getenv().containsKey(ENV_CORRLANG_PORT)) {
                return Integer.parseInt(System.getenv(ENV_CORRLANG_PORT));
            } else {
                Path portFile = corrLangHome.resolve("PORT");
                if (Files.exists(portFile)) {
                    try {
                        return Integer.parseInt(Files.readString(portFile).trim());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return 6969;
                }
            }
        });
    }



    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter helpFormatter = HelpFormatter.builder().setShowSince(false).get();
        try {
            Options options = makeOptions();
            CommandLine line = parser.parse(options, args);
            if (line.hasOption("help")) {
                helpFormatter.printHelp(
                        "corrl [OPTIONS ...] CMD [ARGS ...]",
                        "The CorrLang CLI offers several commands for installing and interacting with the CorrLang runtime.\n" +
                                "Available commands:\n" +
                                " * " + INFO_CMD + "\n" +
                                " * " + STATUS_CMD + "\n" +
                                " * " + INSTALL_CMD + "\n" +
                                " * " + UP_CMD + "\n" +
                                " * " + DOWN_CMD + "\n" +
                                " * " + LIST_CMD + "\n" +
                                " * " + GET_CMD + "\n" +
                                " * " + APPLY_CMD + "\n\n" +
                                "Happy Linking!",
                        options,
                        "Please report issues on <https://codeberg.org/drstrudel/corrlang>!",
                        true);
            } else {
                String[] remainingArgs = line.getArgs();
                if (remainingArgs.length == 0) {
                    System.err.println("Required argument CMD is missing!");
                    System.err.println("Usage: corrl CMD [ARGS ...] [OPTIONS ...]");
                    System.err.println("Use --help to see available commands.");
                    System.exit(1);
                }
                switch (remainingArgs[0]) {
                    case INFO_CMD -> performInfo(line);
                    case STATUS_CMD -> performStatus(line);
                    case INSTALL_CMD -> performInstall(line);
                    case UP_CMD -> performServiceUp(line);
                    case DOWN_CMD -> performServiceDown(line);
                    case LIST_CMD -> performList(line, remainingArgs);
                    case APPLY_CMD -> performApply(line);
                    case GET_CMD -> performGet(line);
                    default -> {
                        System.err.println("Unrecognized CMD: '" + remainingArgs[0]);
                        System.exit(1);
                    }
                }
            }
        } catch (ParseException | URISyntaxException ex) {
            System.err.println(ex.getMessage());
        }
    }

    /**
     * Displays version and installation information.
     */
    private static void performInfo(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        Path libDir = corrLangHome.resolve("lib");
        System.out.println(LOGO);
        System.out.println("CorrLang CLI version: " + VERSION);
        System.out.println("CorrLang home: " + corrLangHome.toAbsolutePath().toString() + File.separator);
        if (Files.exists(libDir) && Files.isDirectory(libDir) ) {
            try (Stream<Path> f = Files.list(libDir)) {
                List<Path> list = f.toList();
                Optional<String> corrLangVersion = list.stream()
                        .map(libDir::relativize)
                        .map(path -> path.getFileName().toString())
                        .filter(path -> path.endsWith(".jar"))
                        .filter(path -> path.startsWith("corrlang-service-"))
                        .map(path -> path.substring(17, path.length() - 4))
                        .findFirst();
                if (corrLangVersion.isPresent()) {
                    System.out.println("CorrLang service version: " + corrLangVersion.get());
                } else {
                    System.out.println("CorrLang installation seems incomplete!");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("CorrLang service is not installed!");
        }
    }


    /**
     * Checks whether the CorrLang core-service is running.
     */
    private static void performStatus(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        int port = getCorrLangPort(line, corrLangHome);
        System.out.println("=== CorrLang Service Status ===");
        try {
            CoreServiceClient client = new CoreServiceClient("localhost", port);
            client.getStatus();
        } catch (StatusRuntimeException e) {
            System.err.println("Unavailable: CorrLang service is not running!");
        }
    }


    /**
     * Stops the CorrLang core service.
     */
    private static void performServiceDown(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        int corrLangPort = getCorrLangPort(line, corrLangHome);
        try {
            CoreServiceClient client = new CoreServiceClient("localhost", corrLangPort);
            client.shutdownService();
            System.out.println("CorrLang service stop requested");
        } catch (StatusRuntimeException e) {
            System.out.println("CorrLang service already topped.");
        }
    }

    /**
     * Starts the CorrLang core service.
     */
    private static void performServiceUp(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        int port = getCorrLangPort(line, corrLangHome);
        try {
            // Check if the service is already running
            CoreServiceClient client = new CoreServiceClient("localhost", port);
            client.checkConnection();
            System.out.println("CorrLang core service is already running.");
        }  catch (StatusRuntimeException e) {
            // If not running, start the service
            ProcessStarter.startCoreServiceProcess(corrLangHome.toFile());
        }
    }


    /**
     * Downloads a specific CorrLang version from the website.
     */
    private static void performInstall(CommandLine line) throws ParseException, IOException, URISyntaxException {
        Path corrLangHome = getCorrLangHome(line);
        int port = getCorrLangPort(line, corrLangHome);
        Installer.downloadAndUnpackZip(corrLangHome.toFile(), true, port);
    }

    /**
     * Get element of the specified type.
     */
    private static void performGet(CommandLine line) {


        // get on endpoint with --tech means schema printing (requires -f to specify output file)

    }

    /**
     * Applies the specified configuration change.
     * Either it expects a CorrSpec file or direct command line parameter.
     */
    private static void performApply(CommandLine line) {
        System.out.println("Performing apply command");

    }

    /**
     * Lists elements of the given type. Available:
     * - techspace(s)
     * - endpoint(s)
     */
    private static void performList(CommandLine line, String[] remainingArgs) throws ParseException {
        if (remainingArgs.length < 2) {
            System.err.println("ERROR: Missing argument for 'list' command. Specify one of: 'techspaces', 'endpoints'");
            System.exit(1);
        } else {
            Path corrLangHome = getCorrLangHome(line);
            int port = getCorrLangPort(line, corrLangHome);
            CoreServiceClient client = new CoreServiceClient("localhost", port);
            switch (remainingArgs[1]) {
                case "techspaces", "techspace", "tech", "techs" -> {
                    client.listTechspaces();
                }
                case "endpoints", "endpoint", "ep", "eps" -> {
                    client.listEndpoints();
                }
                default -> {
                    System.err.println("ERROR: Unrecognized argument for 'list' command: '" + remainingArgs[1] + "'. Specify one of: 'techspaces', 'endpoints'");
                    System.exit(1);
                }
            }
        }
    }

}
