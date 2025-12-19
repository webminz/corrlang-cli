package io.corrlang.cli;

import io.corrlang.protocol.Ccp;
import io.corrlang.protocol.Core;
import io.corrlang.protocol.CoreServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs a system test of the whole application, step by step.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SystemTest {

    private static final Path INSTALL_DIR = Path.of("build", "tempInstallDir");
    private static final int MIN_EXPECTED_API_VERSION = 2;
    private static final Logger logger = LoggerFactory.getLogger(SystemTest.class);
    private static final int MAX_TRIES = 10;
    private Integer testEndpointId = 1;
    private String defaultProject = System.getProperty("user.dir");


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(INSTALL_DIR);
        Files.walkFileTree(INSTALL_DIR, DeleteFileVisitor.getInstance());
        logger.info("Cleaned up installation directory: " + INSTALL_DIR.toAbsolutePath().toString());
    }

    private void waitForStartup(CoreServiceClient client) throws InterruptedException {
        int currentTries = 0;
        while (currentTries < MAX_TRIES) {
            if (client.checkConnection()) {
                return;
            }
            currentTries++;
            Thread.sleep(100L * currentTries);
        }
        Assertions.fail("CorrLang service did not start within expected time.");
    }

    private void waitForShutdown(CoreServiceClient client) throws InterruptedException {
        int currentTries = 0;
        while (currentTries < MAX_TRIES) {
            if (!client.checkConnection()) {
                return;
            }
            currentTries++;
            Thread.sleep(100L * currentTries);
        }
        Assertions.fail("CorrLang service did not stop within expected time.");
    }

    /**
     * Tests installing CorrLang into a fresh directory.
     * This includes downloading the package, unpacking it,
     * and writing the configuration file.
     * The test checks that the directory layout is as expected.
     */
    @Test
    @Order(1)
    public void testDownloadAndInstall() throws Exception {
        logger.info("=== 01: Test installation ===");
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        Runner runner = new Runner();
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " -P " + port + " install");
        Dto installResult = runner.run(new String[]{
                "-H",
                INSTALL_DIR.toAbsolutePath().toString(),
                "-P",
                Integer.toString(port),
                "install"});
        installResult.print();
        Assertions.assertInstanceOf(Dto.CorrLangInstalled.class, installResult);
        Dto.CorrLangInstalled install = (Dto.CorrLangInstalled) installResult;
        Assertions.assertEquals(INSTALL_DIR.toFile().getAbsolutePath(), install.installPath());
        Assertions.assertEquals(CorrLangVersions.CURRENT_VERSION, install.version());
        Assertions.assertFalse(install.didOverwrite());

        Path binDir = INSTALL_DIR.resolve("bin");
        Path libDir = INSTALL_DIR.resolve("lib");
        Path configFile = INSTALL_DIR.resolve("config.toml");
        Assertions.assertTrue(Files.exists(configFile));
        Assertions.assertTrue(Files.exists(binDir));
        Assertions.assertTrue(Files.exists(libDir));

        Stream<Path> pathStream = Files.list(libDir);
        long corrlangJars = pathStream.map(Path::toFile).filter(f -> f.getName().startsWith("corrlang")).filter(f -> f.getName().endsWith(".jar")).count();
        Assertions.assertEquals(3, corrlangJars); // core, service, plugins
        pathStream.close();

        pathStream = Files.list(binDir);
        Set<String> collected = pathStream.map(Path::toFile).map(File::getName).collect(Collectors.toSet());
        pathStream.close();

        Assertions.assertEquals(Set.of("corrlang-service", "corrlang-service.bat"), collected);

        String configContent = Files.readString(configFile);
        int idx = configContent.indexOf("[logging]");
        String header = configContent.substring(0, idx);
        String trailer = configContent.substring(idx);
        Assertions.assertEquals("[system]\nport = " + port + "\nhome = \"" + INSTALL_DIR.toAbsolutePath().toString() + "\"\n\n", header);
        InputStream resourceAsStream = SystemTest.class.getResourceAsStream("/config.template.toml");
        Assertions.assertNotNull(resourceAsStream);
        String expected = new String(resourceAsStream.readAllBytes());
        resourceAsStream.close();
        Assertions.assertEquals(expected, trailer);
    }


    /**
     * Tries to install CorrLang into the same directory again, this time with overwrite enabled.
     * Afterwards, it checks that the installation was successful and that the overwrite flag was recognized.
     */
    @Test
    @Order(2)
    public void testInstallOverwrite() throws Exception {
        logger.info("=== 02: Test install/overwrite ===");
        Runner runner = new Runner();
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " --overwrite install");
        Dto installResult = runner.run(new String[]{
                "-H",
                INSTALL_DIR.toAbsolutePath().toString(),
                "--overwrite",
                "install"});
        installResult.print();
        Assertions.assertInstanceOf(Dto.CorrLangInstalled.class, installResult);
        Dto.CorrLangInstalled install = (Dto.CorrLangInstalled) installResult;
        Assertions.assertEquals(INSTALL_DIR.toFile().getAbsolutePath(), install.installPath());
        Assertions.assertEquals(CorrLangVersions.CURRENT_VERSION, install.version());
        Assertions.assertTrue(install.didOverwrite());

        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " info");
        Dto run = runner.run(new String[]{
                "-H", INSTALL_DIR.toAbsolutePath().toString(),
                "info"
        });
        Assertions.assertInstanceOf(Dto.CorrLangInstallInfo.class, run);
        Dto.CorrLangInstallInfo info = (Dto.CorrLangInstallInfo) run;
        Assertions.assertEquals(Runner.CLI_VERSION, info.cliVersion());
        Assertions.assertEquals(INSTALL_DIR.toFile().getAbsolutePath(), info.corrLangHome());
        Assertions.assertEquals(CorrLangVersions.CURRENT_VERSION, info.corrLangVersion());

    }

    /**
     * Trying install a third time, this time without overwrite enabled.
     * The test expects an exception to be thrown, indicating that the installation already exists.
     */
    @Test
    @Order(3)
    public void testInstallNoOverwrite() {
        logger.info("== 03: Test install/no-overwrite ===");
        Runner runner = new Runner();
        try {
            runner.run(new String[]{
                    "-H",
                    INSTALL_DIR.toAbsolutePath().toString(),
                    "--no-overwrite",
                    "install"});
            Assertions.fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            // already exists
            Assertions.assertTrue(e.getMessage().contains(INSTALL_DIR.toString()));
        }
    }

    @Test
    @Order(4)
    public void testStartup() throws Exception {
        logger.info("=== 04: Test startup ===");
        Instant testStart = Instant.now();
        Stream<Path> pathStream = Files.list(INSTALL_DIR);
        Set<String> collect = pathStream.map(Path::toFile).map(File::getName).collect(Collectors.toSet());
        pathStream.close();
        Assertions.assertFalse(collect.contains("PORT"));

        // Starting process
        Runner runner = new Runner();
        Dto runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "up"});
        runResult.print();
        Assertions.assertInstanceOf(Dto.CorrLangServiceStarted.class, runResult);
        Dto.CorrLangServiceStarted started = (Dto.CorrLangServiceStarted) runResult;
        Assertions.assertFalse(started.alreadyRunning());

        int port = started.port();
        logger.info("Determined CorrLang service port for tests: " + port);

        CoreServiceClient client = new CoreServiceClient("localhost", port);
        waitForStartup(client);

        Dto.CorrLangServiceStatus status = client.getStatus();
        Assertions.assertTrue(status.isRunning());
        Instant now = Instant.now();

        Assertions.assertNotNull(status.pid());
        Assertions.assertTrue(MIN_EXPECTED_API_VERSION <= status.apiVersion());
        Assertions.assertTrue(testStart.compareTo(status.startupTS()) <= 0);
        Assertions.assertTrue(status.startupTS().isBefore(now));

    }

    @Test
    @Order(5)
    public void testStartAgain() throws Exception {
        logger.info("=== 05: Test start/again ===");
        Runner runner = new Runner();
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " up");
        Dto runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "up"});
        runResult.print();
        Assertions.assertInstanceOf(Dto.CorrLangServiceStarted.class, runResult);
        Dto.CorrLangServiceStarted started = (Dto.CorrLangServiceStarted) runResult;
        Assertions.assertTrue(started.alreadyRunning());


        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " status");
        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "status"});
        runResult.print();
        Assertions.assertInstanceOf(Dto.CorrLangServiceStatus.class, runResult);
        Dto.CorrLangServiceStatus status = (Dto.CorrLangServiceStatus) runResult;
        Assertions.assertTrue(status.isRunning());
    }



    @Test
    @Order(6)
    public void testList() throws Exception {
        logger.info("=== 06: Test list ===");
        Runner runner = new Runner();
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " plugins");
        Dto run = runner.run(new String[]{
                "-H", INSTALL_DIR.toAbsolutePath().toString(),
                "plugins"});
        run.print();
        Assertions.assertInstanceOf(Dto.CorrLangTechSpaces.class, run);
        Dto.CorrLangTechSpaces techSpaces = (Dto.CorrLangTechSpaces) run;
        Assertions.assertEquals(Set.of("PUML", "JSON", "GRAPH_QL"), new HashSet<>(techSpaces.techspaces()));

        // nothing created yet, therfore all empty
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " list");
        run = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "list", });
        run.print();
        Assertions.assertInstanceOf(Dto.CorrLangObjects.class, run);
        Dto.CorrLangObjects endpoints = (Dto.CorrLangObjects) run;
        Assertions.assertTrue(endpoints.objects().isEmpty());


        // details of a techspace
        System.out.println("> corrl -H " + INSTALL_DIR.toAbsolutePath().toString() + " get -t JSON");
        run = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "get",
                "-t", "JSON"
        });
        run.print();
        Assertions.assertInstanceOf(Dto.TechSpaceDetails.class, run);
        Dto.TechSpaceDetails details = (Dto.TechSpaceDetails) run;
        Assertions.assertEquals("JSON", details.name());
        Assertions.assertEquals("The CorrLang team <mailto:info@corrlang.io>", details.developer());
        Set<String> actualCapabilities = new HashSet<>(details.capabilities());

        Set<String> expectedCapabilities = Set.of(
                "PARSE_SCHEMA",
                "PARSE_SCHEMA_FROM_URL",
                "PARSE_DATA",
                "SERIALIZE_DATA"
        );
        Assertions.assertEquals(expectedCapabilities, actualCapabilities);

    }

    @Test
    @Order(7)
    public void testCreateEndpointWithSchema() throws Exception {
        Runner runner = new Runner();
        String file = SystemTest.class.getResource("/testSchemaFile.json").getFile();
        // creates an endpoint and registers a schema
        Dto createResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "apply",
                "-e", "TestEndpoint",
                "-t", "JSON",
                "-k", "DATASET",
                "--schema", file
        });
        Assertions.assertInstanceOf(Dto.CorrLangObjectCreated.class, createResult);
        Dto.CorrLangObjectCreated created = (Dto.CorrLangObjectCreated) createResult;
        Assertions.assertEquals(defaultProject, created.project());
        Assertions.assertEquals("TestEndpoint", created.name());
        Assertions.assertEquals("endpoint", created.type());
        testEndpointId = created.id();

        Dto listResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "list" });
        Assertions.assertInstanceOf(Dto.CorrLangObjects.class, listResult);
        Dto.CorrLangObjects endpoints = (Dto.CorrLangObjects) listResult;
        Assertions.assertEquals(1, endpoints.objects().size());
        Dto.CorrLangObject endpoint = endpoints.objects().getFirst();

        Assertions.assertEquals("TestEndpoint", endpoint.name());
        Assertions.assertEquals(defaultProject, endpoint.project());
        Assertions.assertEquals(testEndpointId, endpoint.id());
        Assertions.assertEquals("endpoint", endpoint.type());

        Path expectedPath = INSTALL_DIR.resolve("schema.puml");
        Dto schemaResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "schema",
                "-e", "TestEndpoint",
                "-f", expectedPath.toAbsolutePath().toString(),
                "-t", "PUML"
        });
        Assertions.assertInstanceOf(Dto.CorrLangSchemaExported.class, schemaResult);
        Dto.CorrLangSchemaExported exported = (Dto.CorrLangSchemaExported) schemaResult;
        Assertions.assertEquals(expectedPath.toAbsolutePath().toString(), exported.resultPath());
        Assertions.assertTrue(Files.exists(expectedPath));
        Assertions.assertTrue(Files.size(expectedPath) > 0);
    }


    @Test
    @Order(8)
    public void testRegisterDataForEndpoint() throws Exception {
        Runner runner = new Runner();
        Assertions.assertNotNull(testEndpointId); // must be set in previous test

        Dto runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "get", "-e", "TestEndpoint" });
        Assertions.assertInstanceOf(Dto.EndpointDetails.class, runResult);
        Dto.EndpointDetails actual = (Dto.EndpointDetails) runResult;
        Dto.EndpointDetails expected = new Dto.EndpointDetails(
                defaultProject,
                "TestEndpoint",
                testEndpointId,
                "DATA",
                true,
                Collections.emptyList(),
                null,
                null,
                null
        );
        Assertions.assertEquals(expected, actual);


        String file = SystemTest.class.getResource("/testDataFile.json").getFile();
        // creates an endpoint and registers a schema
        Dto createResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "apply",
                "-e", "TestEndpoint",
                "-t", "JSON",
                "--data", file
        });
        Assertions.assertInstanceOf(Dto.CorrLangObjectUpdated.class, createResult);
        Dto.CorrLangObjectUpdated updated = (Dto.CorrLangObjectUpdated) createResult;
        Assertions.assertEquals(new Dto.CorrLangObjectUpdated(
                defaultProject,
                "TestEndpoint",
                testEndpointId,
                "endpoint"
        ), updated);


        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "get", "-e", "TestEndpoint" });
        Assertions.assertInstanceOf(Dto.EndpointDetails.class, runResult);
        Dto.EndpointDetails updatedEndpoint = (Dto.EndpointDetails) runResult;
        Assertions.assertEquals(1, updatedEndpoint.datasets().size());
        Dto.Dataset ds = updatedEndpoint.datasets().getFirst();
        Assertions.assertNotNull(ds.uuid());
        Assertions.assertEquals("file://" + file, ds.url());

    }

    @Test
    @Order(9)
    public void testRegisterOtherObjects() throws Exception {
        Runner runner = new Runner();
        // register first endpoint 1
        Dto runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "apply",
                "-p", "OtherProject",
                "-e", "Sales",
                "-t", "GRAPH_QL",
                "-k", "SERVICE",
                "--service", "http://localhost:4000/"
        });
        Assertions.assertInstanceOf(Dto.CorrLangObjectCreated.class, runResult);
        Dto.CorrLangObjectCreated create1 = (Dto.CorrLangObjectCreated) runResult;
        Assertions.assertEquals("endpoint", create1.type());
        Assertions.assertEquals("OtherProject", create1.project());
        Assertions.assertEquals("Sales", create1.name());
        int ep1 = create1.id();

        // register endpoint 2
        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "apply",
                "-p", "OtherProject",
                "-e", "Invoices",
                "-t", "GRAPH_QL",
                "-k", "SERVICE",
                "--service", "http://localhost:4001/"
        });
        Assertions.assertInstanceOf(Dto.CorrLangObjectCreated.class, runResult);
        Dto.CorrLangObjectCreated create2 = (Dto.CorrLangObjectCreated) runResult;
        Assertions.assertEquals("endpoint", create2.type());
        Assertions.assertEquals("Invoices", create2.name());
        Assertions.assertEquals("OtherProject", create2.project());
        int ep2 = create2.id();

        // register endpoint 3
        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "apply",
                "-p", "OtherProject",
                "-e", "Hr",
                "-t", "GRAPH_QL",
                "-k", "SERVICE",
                "--service", "http://localhost:4001/"
        });
        Assertions.assertInstanceOf(Dto.CorrLangObjectCreated.class, runResult);
        Dto.CorrLangObjectCreated create3 = (Dto.CorrLangObjectCreated) runResult;
        Assertions.assertEquals("endpoint", create3.type());
        Assertions.assertEquals("Hr", create3.name());
        Assertions.assertEquals("OtherProject", create3.project());
        int ep3 = create3.id();

        // registering a correspondence and a view directly via gRPC, as these are
        // currently not yet accessible via CLI
        int port = Integer.parseInt(Files.readString(INSTALL_DIR.resolve("PORT")).trim());
        CoreServiceGrpc.CoreServiceBlockingStub rawClient = CoreServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build());
        Ccp.Correspondence correspondence = rawClient.registerCorrespondence(Core.RegisterCorrespondenceRequest.newBuilder()
                .setName("Corr")
                .setProject("OtherProject")
                .addEndpoints(ep1)
                .addEndpoints(ep2)
                .addEndpoints(ep3)
                .build());
        Ccp.View view = rawClient.registerView(Core.RegisterViewRequest.newBuilder()
                .setType(Ccp.EndpointType.SERVICE)
                .setProject("OtherProject")
                .setName("Global")
                .setCorrespondence(correspondence.getId())
                .build());


        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "get",
                "-e", "Sales",
                "-p", "OtherProject"});
        Assertions.assertInstanceOf(Dto.EndpointDetails.class, runResult);
        Dto.EndpointDetails salesEndpoint = (Dto.EndpointDetails) runResult;
        Assertions.assertEquals(new Dto.EndpointDetails(
                "OtherProject",
                "Sales",
                ep1,
                "SERVICE",
                false,
                Collections.emptyList(),
                "http://localhost:4000/",
                "localhost",
                4000

        ), salesEndpoint);


        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "get",
                "-c", "Corr",
                "-p", "OtherProject"});
        Assertions.assertInstanceOf(Dto.CorrespondenceDetails.class, runResult);
        Dto.CorrespondenceDetails correspondenceDetails = (Dto.CorrespondenceDetails) runResult;
        Assertions.assertEquals(new Dto.CorrespondenceDetails(
                "OtherProject",
                "Corr",
                correspondence.getId(),
                Arrays.asList("Sales", "Invoices", "Hr")
        ), correspondenceDetails);

        runResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                "get",
                "-v", "Global",
                "-p", "OtherProject"});
        Assertions.assertInstanceOf(Dto.ViewDetails.class, runResult);
        Dto.ViewDetails viewDetails = (Dto.ViewDetails) runResult;
        Assertions.assertEquals(new Dto.ViewDetails(
                "OtherProject",
                "Global",
                view.getId(),
                "Corr",
                "SERVICE"
        ), viewDetails);
    }


    @Test
    @Order(10)
    public void testIllegalArguments() {

        // fail: register server address for dataset entdpoint
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
                    "-e", "TestEndpoint",
                    "-t", "GRAPH_QL",
                    "--service", "http://localhost:9000/"
            });
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertEquals("Operation not permitted! (reason: Endpoint with id:1 is of type 'DATASET' but it would need to be of type 'SERVICE' or 'SINK'!)", e.getMessage());
        }

        // fail: register dataset for service endpoint
        try  {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
                    "-e", "Hr",
                    "-p", "OtherProject",
                    "-t", "JSON",
                    "--data", getClass().getResource("/testDataFile.json").getFile()
            });
            Assertions.fail();
        } catch (Exception e) {
            Assertions.assertEquals("Operation not permitted! (reason: Endpoint with id:4 is of type 'SERVICE' but it would need to be of type 'DATASET'!)", e.getMessage());
        }

        // fail: unknown tech space
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
                    "-e", "WillFail",
                    "-p", "RestProject",
                    "-t", "FANCY_TECHNOLOGY_NOBODY_EVERY_HEARD_OF",
                    "-k", "SERVICE",
                    "--schema", getClass().getResource("/testSchemaFile.json").getFile()
            });
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertEquals("Could not find 'FANCY_TECHNOLOGY_NOBODY_EVERY_HEARD_OF' in 'techspaces'", ex.getMessage());
        }

        // fail: apply without arguments
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
            });
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertEquals("Missing required option! Please specify -f <file> to apply a CorrSpec file or use direct modification options!", ex.getMessage());
        }


        // fail: creating new endpoint without specifying kind
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
                    "-e", "WillAlsoFail",
                    "-p", "RestProject",
                    "-t", "JSON",
                    "--schema", getClass().getResource("/testSchemaFile.json").getFile()
            });
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertEquals("Missing required option '-k <endpoint-kind>'!", ex.getMessage());
        }


        // fail: retrieve objects that do not exist
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "get",
                    "-e", "FantasyProject",
            });
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertEquals("Cannot find endpoint with name 'FantasyProject' in project '/Users/past-madm/Projects/corrlang/cli'.", ex.getMessage());
        }


        // fail: register dataset endpoint without schema
        try {
            Runner runner = new Runner();
            runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(),
                    "apply",
                    "-e", "NoSchemaEndpoint",
                    "-p", "RestProject",
                    "-t", "JSON",
                    "-k", "DATASET",
                    "--data", getClass().getResource("/testDataFile.json").getFile()
            });
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertEquals("Operation not permitted! (reason: Endpoint with id:8 has no schema defined!)", ex.getMessage());
        }
    }


    @Test
    @Order(11)
    public void testFinalList() throws Exception {
        Runner runner = new Runner();
        Dto listResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "list" });
        Assertions.assertInstanceOf(Dto.CorrLangObjects.class, listResult);
        Dto.CorrLangObjects endpoints = (Dto.CorrLangObjects) listResult;
        Assertions.assertEquals(1, endpoints.objects().size());
        Dto.CorrLangObject firstEndpoint = endpoints.objects().getFirst();
        Assertions.assertEquals("TestEndpoint", firstEndpoint.name());
        Assertions.assertEquals(1, firstEndpoint.id());
        Assertions.assertEquals("endpoint", firstEndpoint.type());

        listResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "list", "-p", "OtherProject" });
        Assertions.assertInstanceOf(Dto.CorrLangObjects.class, listResult);
        Dto.CorrLangObjects otherObjects = (Dto.CorrLangObjects) listResult;
        Assertions.assertEquals(5, otherObjects.objects().size());
        Assertions.assertEquals(3, otherObjects.objects().stream().filter(o -> o.type().equals("endpoint")).count());
        Assertions.assertEquals(1, otherObjects.objects().stream().filter(o -> o.type().equals("view")).count());
        Assertions.assertEquals(1, otherObjects.objects().stream().filter(o -> o.type().equals("correspondence")).count());

        listResult = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "list", "--all" });
        Assertions.assertInstanceOf(Dto.CorrLangObjects.class, listResult);
        otherObjects = (Dto.CorrLangObjects) listResult;
        otherObjects.print();
        Assertions.assertEquals(6, otherObjects.objects().size()); // 1 endoint in the default project, 5 in OtherProject

    }


    @Test
    @Order(12)
    public void testShutdown() throws Exception {
        logger.info("Final test --> shutting down");
        // Stopping process
        Runner runner = new Runner();
        runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "down"});


        int port = Integer.parseInt(Files.readString(INSTALL_DIR.resolve("PORT")).trim());
        CoreServiceClient client = new CoreServiceClient("localhost", port);
        waitForShutdown(client);

        Dto run = runner.run(new String[]{"-H", INSTALL_DIR.toAbsolutePath().toString(), "status"});
        Assertions.assertInstanceOf(Dto.CorrLangServiceStatus.class, run);
        Dto.CorrLangServiceStatus status = (Dto.CorrLangServiceStatus) run;
        Assertions.assertFalse(status.isRunning());
    }
}
