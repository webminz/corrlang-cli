package io.corrlang.cli;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import org.apache.commons.cli.*;
import org.apache.commons.cli.help.HelpFormatter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class Runner {

    public static final String CLI_VERSION = "1.0-SNAPSHOT-alpha";

    public static final String LOGO =
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
    public static final String HELP_CMD = "help";
    public static final String SCHEMA_CMD = "schema";
    public static final String PLUGINS_CMD = "plugins";

    public static final String SHORT_OPT_CORRLANG_HOME = "H";
    public static final String LONG_OPT_CORRLANG_HOME = "home";
    public static final String ENV_CORRLANG_HOME = "CORRLANG_HOME";

    public static final String ENV_CORRLANG_PORT = "CORRLANG_PORT";
    public static final String SHORT_OPT_CORRLANG_PORT = "P";
    public static final String LONG_OPT_CORRLANG_PORT = "port";

    public static final String ENV_CORRLANG_JAVA = "CORRLANG_JAVA";
    public static final String SHORT_OPT_CORRLANG_JAVA = "J";
    public static final String LONG_OPT_CORRLANG_JAVA = "java";

    public static final String SHORT_OPT_CORRLANG_VERSIOn = "V";
    public static final String LONG_OPT_CORRLANG_VERSION = "version";

    private static final String ENDPOINT_KIND_OPTION_DATASET = "dataset";
    private static final String ENDPOINT_KIND_OPTION_SERVICE = "service";
    private static final String ENDPOINT_KIND_OPTION_SOURCE = "source";
    private static final String ENDPOINT_KIND_OPTION_SINK = "sink";
    private static final int MAX_TRIES = 10;


    private final Option fileOption = Option.builder()
            .option("f")
            .longOpt("file")
            .hasArg()
            .type(Path.class)
            .desc("The path of a 'spec(ification)' file that shall be applied")
            .get();

    private final Option techSpaceOption = Option.builder()
            .option("t")
            .longOpt("tech")
            .hasArg()
            .type(String.class)
            .desc("The name of the 'techspace' that shall be used for the operation")
            .get();

    private final Option projectOption = Option.builder()
            .option("p")
            .longOpt("project")
            .hasArg()
            .type(String.class)
            .desc("The name of the 'project' that shall be used for the operation")
            .get();


    private final Option endpointOption = Option.builder()
            .option("e")
            .longOpt("endpoint")
            .hasArg()
            .type(String.class)
            .desc("The name of the 'endpoint' that shall be used for the operation")
            .get();

    private final Option correspondenceOption = Option.builder()
            .option("c")
            .longOpt("correspondence")
            .hasArg()
            .type(String.class)
            .desc("The name of the 'correspondence' that shall be used for the operation")
            .get();

    private final Option viewOption = Option.builder()
            .option("v")
            .longOpt("view")
            .hasArg()
            .type(String.class)
            .desc("The name of the 'view' that shall be used for the operation")
            .get();

    private final Option schemaOption = Option.builder()
            .longOpt("schema")
            .desc("Expects the file path of a schema and registers this schema for the specified endpoint.")
            .type(String.class)
            .hasArg()
            .get();

    private final Option endpointKind = Option.builder()
            .option("k")
            .longOpt("endpoint-kind")
            .desc("Specifies the endpoint type.")
            .type(String.class)
            .hasArg()
            .get();

    private final Option dataOption = Option.builder()
            .longOpt("data")
            .hasArgs()
            .type(String.class)
            .desc("Expects a file path and loads the file contents for a dataset endpoint.")
            .get();

    private final Option serviceOption = Option.builder()
            .longOpt("service")
            .hasArg()
            .type(String.class)
            .desc("Sets the URL of the specified endpoint to the given value.")
            .get();

    private final Option allOption = Option.builder()
            .option("a")
            .longOpt("all")
            .desc("If specified, all elements of the given type will be listed.")
            .hasArg(false)
            .get();

    private Options makeOptions() {
        Options result = new Options();

        result.addOption("h", HELP_CMD, false, "Display this help window.");

        result.addOption(techSpaceOption);
        result.addOption(projectOption);
        result.addOption(endpointOption);
        result.addOption(correspondenceOption);
        result.addOption(viewOption);
        result.addOption(endpointKind);

        OptionGroup directModifications = new OptionGroup();
        directModifications.addOption(fileOption);
        directModifications.addOption(schemaOption);
        directModifications.addOption(dataOption);
        directModifications.addOption(serviceOption);
        result.addOptionGroup(directModifications);


        OptionGroup overwriteOption = new OptionGroup();
        overwriteOption.addOption(Option.builder()
                .longOpt("overwrite")
                .desc("If specified, a potentially existing CorrLang installation at CORRLANG_HOME will be overwritten when calling 'install'. Default is 'true'.")
                        .hasArg(false)
                .get());
        overwriteOption.addOption(Option.builder()
                .longOpt("no-overwrite")
                .desc("If specified, a potentially existing CorrLang installation at CORRLANG_HOME will NOT be overwritten when calling 'install'. Default is 'false'.")
                        .hasArg(false)
                .get());
        result.addOptionGroup(overwriteOption);

        result.addOption(allOption);


        result.addOption(corrlangPort);
        result.addOption(corrlangJava);
        result.addOption(corrlangHome);
        result.addOption(corrlangVersion);
        return result;
    }

    private final Option corrlangVersion = Option.builder()
            .option(SHORT_OPT_CORRLANG_VERSIOn)
            .option(LONG_OPT_CORRLANG_VERSION)
            .hasArg()
            .type(String.class)
            .desc("Specifies the CorrLang version that shall be installed. " +
                    "If not specified, the latest stable version will be installed.")
            .get();

    private final Option corrlangJava = Option.builder()
            .option(SHORT_OPT_CORRLANG_JAVA)
            .option(LONG_OPT_CORRLANG_JAVA)
            .hasArg()
            .type(String.class)
            .desc("Specifies the path of the JVM that" +
                    " shall be used to run the core-service. If not specified it will use the environment variable " + ENV_CORRLANG_JAVA + "," +
                    " then it will consult JAVA_HOME, and a last resort it will check if there is `java` on the PATH.")
            .get();


    private final Option corrlangPort = Option.builder()
            .option(SHORT_OPT_CORRLANG_PORT)
            .longOpt(LONG_OPT_CORRLANG_PORT)
            .hasArg()
            .type(Integer.class)
            .desc("The TCP port number of the CorrLang core-service for IPC. If not specified" +
                    " it will look for an environment variable " + ENV_CORRLANG_PORT + ", afterwards for a PORT file" +
                    " in the CorrLang installation directory and then will fall back to the default 6969.")
            .get();

    private final Option corrlangHome = Option.builder()
            .option(SHORT_OPT_CORRLANG_HOME)
            .longOpt(LONG_OPT_CORRLANG_HOME)
            .hasArg()
            .type(Path.class)
            .desc("Uses the specified CorrLang " +
                    "installation directory. If not specified, the environment variable " + ENV_CORRLANG_HOME + " is used. And if " +
                    "the latter was not specified either, the default directory is 'USER_HOME/.corrlang/'.")
            .get();



    private Path getCorrLangHome(CommandLine line) throws ParseException {
        return line.getParsedOptionValue(corrlangHome, () -> {
            if (System.getenv().containsKey(ENV_CORRLANG_HOME)) {
                return Path.of(System.getenv(ENV_CORRLANG_HOME));
            } else {
                return Path.of(System.getProperty("user.home"), DEFAULT_CORRLANG_INSTALL_DIRNAME);
            }
        });
    }

    private  int getCorrLangPort(CommandLine line, Path corrLangHome) throws ParseException {
        return line.getParsedOptionValue(corrlangPort, () -> {
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

    private String getEndpointKind(CommandLine line) throws ParseException {
        if (!line.hasOption(endpointKind)) {
            throw new ParseException("Missing required option '-k <endpoint-kind>'!");
        }
        String kind = line.getParsedOptionValue(endpointKind);
        kind = kind.toLowerCase();
        if (!kind.equals(ENDPOINT_KIND_OPTION_DATASET) &&
                !kind.equals(ENDPOINT_KIND_OPTION_SERVICE) &&
                !kind.equals(ENDPOINT_KIND_OPTION_SOURCE) &&
                !kind.equals(ENDPOINT_KIND_OPTION_SINK)) {
            throw new ParseException("Unknown endpoint kind specified: '" + kind + "'. Valid options are: '" +
                    ENDPOINT_KIND_OPTION_DATASET + "', '" + ENDPOINT_KIND_OPTION_SERVICE + "', '" +
                    ENDPOINT_KIND_OPTION_SOURCE + "', '" + ENDPOINT_KIND_OPTION_SINK + "'.");
        }
        return kind;
    }

    private String getCorrlangVersion(CommandLine line) throws ParseException {
        String version = line.getParsedOptionValue(corrlangVersion, CorrLangVersions.CURRENT_VERSION);
        if (!CorrLangVersions.isValid(version)) {
            throw new ParseException("Unknown CorrLang version specified: '" + version + "'.");
        }
        return version;
    }

    public Dto run(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter helpFormatter = HelpFormatter.builder().setShowSince(false).get();

        Options options = makeOptions();
        CommandLine line = parser.parse(options, args);
        if (line.hasOption(HELP_CMD)) {
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
                            " * " + SCHEMA_CMD + "\n\n" +
                            " * " + PLUGINS_CMD + "\n\n" +
                            "Happy Linking!",
                    options,
                    "Please report issues on <https://codeberg.org/drstrudel/corrlang>!",
                    true);
            return null;
        } else {
            String[] remainingArgs = line.getArgs();
            if (remainingArgs.length == 0) {
                System.err.println("Required argument CMD is missing!");
                System.err.println("Usage: corrl CMD [ARGS ...] [OPTIONS ...]");
                System.err.println("Use --help to see available commands.");
                System.exit(1);
            }
            try {
                return switch (remainingArgs[0]) {
                    case INFO_CMD -> performInfo(line);
                    case STATUS_CMD -> performStatus(line);
                    case INSTALL_CMD -> performInstall(line);
                    case UP_CMD -> performServiceUp(line);
                    case DOWN_CMD -> performServiceDown(line);
                    case LIST_CMD -> performList(line);
                    case GET_CMD -> performGet(line);
                    case APPLY_CMD -> performApply(line);
                    case SCHEMA_CMD -> performSchema(line);
                    case PLUGINS_CMD -> performPlugins(line);
                    default -> {
                        throw new ParseException("Unknown command: " + remainingArgs[0]);
                    }
                };
            } catch (StatusRuntimeException e) {
                Metadata trailers = e.getTrailers();
                assert trailers != null;
                String message = trailers.get(Metadata.Key.of("message", Metadata.ASCII_STRING_MARSHALLER));
                throw new RuntimeException(message, e);
            }

        }
    }

    private Dto performPlugins(CommandLine line) throws ParseException {
        CoreServiceClient client = makeClient(line);
        return client.listTechspaces();
    }


    public static void main(String[] args) throws IOException {
        System.setProperty("org.apache.commons.cli.help.width", "120");
        System.setProperty("io.netty.noUnsafe", "true");

        Runner runner = new Runner();
        try {
            Dto result = runner.run(args);
            if (result != null) {
                result.print();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Displays version and installation information.
     */
    private Dto.CorrLangInstallInfo performInfo(CommandLine line) throws Exception {
        Path corrLangHome = getCorrLangHome(line);
        return new Dto.CorrLangInstallInfo(
                CLI_VERSION,
                corrLangHome.toAbsolutePath().toString(),
                Installer.getInstalledVersion(corrLangHome).orElse(null)
        );
    }

    /**
     * Checks whether the CorrLang core-service is running.
     */
    private Dto.CorrLangServiceStatus performStatus(CommandLine line) throws ParseException {
        CoreServiceClient client = makeClient(line);
        return client.getStatus();
    }


    /**
     * Downloads a specific CorrLang version from the website.
     */
    private Dto.CorrLangInstalled performInstall(CommandLine line) throws Exception {
        Path corrLangHome = getCorrLangHome(line);
        int port = getCorrLangPort(line, corrLangHome);
        String version = getCorrlangVersion(line);
        boolean overwrite = isOverwrite(line);
        return Installer.downloadAndUnpackZip(corrLangHome, version, overwrite, port);
    }

    /**
     * Starts the CorrLang core service.
     */
    private Dto.CorrLangServiceStarted performServiceUp(CommandLine line) throws ParseException, IOException {
        Path corrLangHome = getCorrLangHome(line);
        Path portFile = corrLangHome.resolve("PORT");
        if (Files.exists(portFile)) {
            int port = Integer.parseInt(Files.readString(portFile));
            return new Dto.CorrLangServiceStarted(port, true);
        } else {
            ProcessStarter.startCoreServiceProcess(corrLangHome.toFile());
            int count = 0;
            while (count < MAX_TRIES) {
                try {
                    Thread.sleep(count * 100L);
                } catch (InterruptedException e) {}
                if (Files.exists(portFile)) {
                    break;
                }
                count++;
            }
            if (!Files.exists(portFile)) {
                throw new RuntimeException("Failed to start CorrLang core service within expected time!");
            }
            int port = Integer.parseInt(Files.readString(portFile));
            return new Dto.CorrLangServiceStarted(port, false);
        }
    }


    /**
     * Stops the CorrLang core service.
     */
    private Dto.CorrLangServiceStopped performServiceDown(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        int corrLangPort = getCorrLangPort(line, corrLangHome);
        CoreServiceClient client = new CoreServiceClient("localhost", corrLangPort);
        if (client.checkConnection()) {
            client.shutdownService();
            return new Dto.CorrLangServiceStopped(true);
        } else {
            return new Dto.CorrLangServiceStopped(false);
        }
    }


    private boolean isOverwrite(CommandLine line) {
        return !line.hasOption("no-overwrite");
    }

    private String getProject(CommandLine line) throws ParseException {
        return line.getParsedOptionValue(projectOption, () -> {
            return System.getProperty("user.dir");
        });
    }

    /**
     * Get element of the specified type.
     */
    private Dto performGet(CommandLine line) throws ParseException {
        CoreServiceClient client = makeClient(line);
        String techSpace = line.getParsedOptionValue(techSpaceOption, () -> null);
        if (techSpace != null) {
            return client.getTechSpaceInfo(techSpace);
        }
        String endpoint = line.getParsedOptionValue(endpointOption, () -> null);
        String project = getProject(line);
        if (endpoint != null) {
            Optional<Integer> maybeEndpoint = client.getEndpointId(project, endpoint);
            if (maybeEndpoint.isEmpty()) {
                throw new IllegalArgumentException("Cannot find endpoint with name '" + endpoint + "' in project '" + project + "'.");
            } else {
                return client.getEndpointInfo(maybeEndpoint.get());
            }
        }

        String correspondence = line.getParsedOptionValue(correspondenceOption, () -> null);
        if (correspondence != null) {

            Optional<Integer> maybeCorr = client.getCorrespondenceId(project, correspondence);
            if (maybeCorr.isEmpty()) {
                throw new IllegalArgumentException("Cannot find correspondence with name '" + correspondence + "' in project '" + project + "'.");
            }
            return client.getCorrespondenceInfo(maybeCorr.get());
        }

        String view = line.getParsedOptionValue(viewOption, () -> null);
        if (view != null) {
            Optional<Integer> maybeView = client.getViewId(project, view);
            if (maybeView.isEmpty()) {
                throw new IllegalArgumentException("Cannot find view with name '" + view + "' in project '" + project + "'.");
            }
            return client.getViewInfo(maybeView.get());
        }

        throw new ParseException("Missing required option! " +
                "Please specify one of '-t <techspace>', '-e <endpoint>', '-c <correspondence>', or '-v <view>' to get the details of the respective object.");
    }


    private CoreServiceClient makeClient(CommandLine line) throws ParseException {
        Path corrLangHome = getCorrLangHome(line);
        int port = getCorrLangPort(line, corrLangHome);
        return new CoreServiceClient("localhost", port);
    }

    /**
     * Applies the specified configuration change.
     * Either it expects a CorrSpec file or direct command line parameter.
     */
    private Dto performApply(CommandLine line) throws ParseException, URISyntaxException, IOException {
        CoreServiceClient client = makeClient(line);
        String project = getProject(line);
        if (line.hasOption(fileOption)) {
            Path base = Path.of(".");
            String absolute = base.toRealPath().toAbsolutePath().toString();
            String corrFile = base.resolve(line.getOptionValue(fileOption)).toString();

            return client.applyCorrSpec(project, absolute,corrFile);
        }

        if (line.hasOption(endpointOption)) {
            String endpoint = line.getParsedOptionValue(endpointOption);
            Optional<Integer> existingEndpoint = client.getEndpointId(project, endpoint);
            int eid;
            Dto.CorrLangObjectCreated createdResult = null;
            if (existingEndpoint.isEmpty()) {
                String kind = getEndpointKind(line);
                createdResult = client.applyAddEndpoint(
                        project,
                        endpoint,
                        kind
                );
                eid = createdResult.id();
            } else {
                eid = existingEndpoint.get();
            }

            if (line.hasOption(schemaOption)) {
                String schema = line.getParsedOptionValue(schemaOption);
                String techSpace = line.getParsedOptionValue(techSpaceOption);

                boolean isUrl = false;
                File file = null;
                String url = null;
                if (schema.startsWith("http")) {
                    url = schema;
                    isUrl = true;
                } else {
                    file = Path.of(schema).toFile();
                    if (file.exists()) {
                        isUrl = false;
                    } else {
                        url = new URI(schema).toURL().toExternalForm(); // validate URL
                        file = null;
                        isUrl = true;
                    }
                }

                try {
                    Dto updatedResult;
                    if (isUrl) {
                        updatedResult = client.applyAddEndpointSchemaURL(eid, techSpace, url);
                    } else {
                        updatedResult = client.applyAddEndpointSchema(eid, techSpace, file);
                    }
                    if (existingEndpoint.isEmpty()) {
                        return createdResult;
                    } else {
                        return updatedResult;
                    }

                } catch (StatusRuntimeException e) {
                    // compensation
                    if (existingEndpoint.isEmpty()) {
                        client.removeEndpoint(eid);
                    }
                    throw e;
                }


            } else if (line.hasOption(dataOption)) {
                String data = line.getParsedOptionValue(dataOption);
                String techSpace = line.getParsedOptionValue(techSpaceOption);


                boolean isUrl = false;
                File file = null;
                String url = null;
                if (data.startsWith("http")) {
                    url = data;
                    isUrl = true;
                } else {
                    file = Path.of(data).toFile();
                    if (file.exists()) {
                        isUrl = false;
                    } else {
                        url = new URI(data).toURL().toExternalForm(); // validate URL
                        file = null;
                        isUrl = true;
                    }
                }

                try {
                    Dto.CorrLangObjectUpdated updatedResult;
                    if (isUrl) {
                        updatedResult = client.applyAddEndpointDataURL(eid, techSpace, url);
                    } else {
                        updatedResult = client.applyAddEndpointData(eid, techSpace, file);
                    }

                    if (endpoint.isEmpty()) {
                        return createdResult;
                    } else {
                        return updatedResult;
                    }
                }  catch (StatusRuntimeException e) {
                    // compensation
                    if (existingEndpoint.isEmpty()) {
                        client.removeEndpoint(eid);
                    }
                    throw e;
                }

            } else if (line.hasOption(serviceOption)) {
                String address = line.getParsedOptionValue(serviceOption);
                String techSpace = line.getParsedOptionValue(techSpaceOption);

                boolean isUrl = true;
                String url = address;
                String host = null;
                Integer port = null;

                if (address.contains(":")) {
                    String[] parts = address.split(":");
                    if (parts.length == 2) {
                        try {
                            port = Integer.parseInt(parts[1]);
                            host = parts[0];
                            isUrl = false;
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }

                try {
                    Dto.CorrLangObjectUpdated updatedResult;
                    if (isUrl) {
                        updatedResult = client.applyAddEndpointServiceURL(eid, techSpace, url);
                    } else {
                        updatedResult = client.applyAddEndpointSocket(eid, techSpace, host, port);
                    }

                    if (existingEndpoint.isEmpty()) {
                        return createdResult;
                    } else {
                        return updatedResult;
                    }
                } catch (StatusRuntimeException e) {
                    // compensation
                    if (existingEndpoint.isEmpty()) {
                        client.removeEndpoint(eid);
                    }
                    throw e;
                }
            } else {
                throw new ParseException("No direct modification option specified! Use either '--schema <file>', '--data <file>', or '--service <url>'!");
            }
        }
        throw new ParseException("Missing required option! Please specify -f <file> to apply a CorrSpec file or use direct modification options!");
    }

    private Dto performSchema(CommandLine line) throws ParseException {
        String project = getProject(line);
        String techSpace = line.getParsedOptionValue(techSpaceOption);
        Path targetFile = line.getParsedOptionValue(fileOption);
        String endpoint = line.getParsedOptionValue(endpointOption);
        CoreServiceClient client = makeClient(line);
        return client.exportEndpointSchema(project, endpoint, techSpace, targetFile);
    }

    /**
     * Lists registered elements.
     */
    private Dto performList(CommandLine line) throws ParseException {
        CoreServiceClient client = makeClient(line);
        String project = getProject(line);
        if (line.hasOption(allOption)) {
            project = null;
        }
        return client.listAll(project);
    }

}
