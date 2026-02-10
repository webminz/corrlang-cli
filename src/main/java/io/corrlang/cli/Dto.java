package io.corrlang.cli;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.corrlang.cli.Runner.LOGO;

/**
 * Container for Data Transfer Objects (DTOs) used in the CorrLang CLI.
 */
sealed interface Dto permits
        Dto.CorrLangInstallInfo,
        Dto.CorrLangInstalled,
        Dto.CorrLangObjectCreated,
        Dto.CorrLangObjectUpdated,
        Dto.CorrLangObjects,
        Dto.CorrLangSchemaExported,
        Dto.CorrLangServiceStarted,
        Dto.CorrLangServiceStatus,
        Dto.CorrLangServiceStopped,
        Dto.CorrLangTechSpaces,
        Dto.CorrLangObjectDetails,
        Dto.CorrSpecMerged
{

    record CorrLangInstallInfo(String cliVersion, String corrLangHome, @Nullable String corrLangVersion) implements Dto {

        @Override
        public void print() {
            System.out.println(LOGO);
            System.out.println("CorrLang CLI version: " + cliVersion);
            System.out.println("CorrLang home: " + corrLangHome + File.separator);
            if (corrLangVersion != null) {
                System.out.println("CorrLang service version: " + corrLangVersion);
            } else {
                System.out.println("CorrLang service not installed.");
            }
        }
    }

    record CorrLangInstalled(String version, String installPath, boolean didOverwrite) implements Dto {

        public void print() {
            System.out.println("CorrLang version " + version + " was successfully installed at: " + installPath);
        }
    }

    record CorrLangServiceStarted(int port, boolean alreadyRunning) implements Dto {

        @Override
        public void print() {

        }
    }

    record CorrLangServiceStopped(boolean wasRunning) implements Dto {

        @Override
        public void print() {
            if (wasRunning) {
                System.out.println("CorrLang core service has been stopped.");
            } else {
                System.out.println("CorrLang core service was not running.");
            }
        }
    }

    record CorrLangServiceStatus(
            boolean isRunning,
            Integer apiVersion,
            Long pid,
            Integer port,
            Instant startupTS) implements Dto {

        @Override
        public void print() {
            System.out.println("=== CorrLang Service Status ===");
            if (isRunning) {
                Instant now = Instant.now();
                Duration duration = Duration.between(startupTS, now);
                System.out.println("Status      : RUNNING");
                System.out.println("API version : " + apiVersion);
                System.out.println("Service PID : " + pid);
                System.out.println("Service Port: TCP/" + port);
                System.out.println("Startup TS  : " + startupTS);
                System.out.println("Uptime      : " + duration.toString());
            } else  {
                System.out.println("Status      : OFFLINE");
            }
        }
    }

    record CorrLangTechSpaces(List<String> techspaces) implements Dto {

        @Override
        public void print() {
            System.out.println("Techspaces");
            for (String ts : techspaces) {
                System.out.println(" - " + ts);
            }
            if (techspaces.isEmpty()) {
                System.out.println("<empty>");
            }
        }
    }

    record CorrLangObject(String type, String project, String name, int id) {}

    record CorrLangObjects(List<CorrLangObject> objects) implements Dto {

        @Override
        public void print() {
            for (CorrLangObject obj : this.objects) {
                System.out.println(" -"+ obj.type + ": " + obj.project + "/" + obj.name + " (oid: " + obj.id + ")");
            }
            if (this.objects.isEmpty()) {
                System.out.println("<empty>");
            }
        }
    }

    record CorrLangObjectCreated(String project, String name, int id, String type) implements Dto {

        @Override
        public void print() {
            System.out.println(type + " '" + name + "' created in project '" + project + "(oid: " + id + ").");
        }
    }


    record CorrLangSchemaExported(String resultPath) implements Dto {

        @Override
        public void print() {
            System.out.println("Schema exported to: " + resultPath);
        }
    }

    sealed interface CorrLangObjectDetails extends Dto permits
            Dto.TechSpaceDetails,
            Dto.EndpointDetails,
            Dto.CorrespondenceDetails,
            Dto.ViewDetails {
    }


    record TechSpaceDetails(String name, String description, String developer, List<String> capabilities) implements CorrLangObjectDetails {

        public void print() {
            System.out.println("===" + name + "===");
            System.out.println(description);
            System.out.println("developed by: " + developer);
            System.out.println("capabilities:");
            for (String cap : capabilities) {
                System.out.println(" - " + cap);
            }
        }
    }

    record Dataset(
            String url,
            String uuid
    ) {}

    record EndpointDetails(
            String project,
            String name,
            int id,
            String type,
            boolean hasSchema,
            List<Dataset> datasets,
            @Nullable String url,
            @Nullable String host,
            @Nullable Integer port
            ) implements Dto.CorrLangObjectDetails  {


        @Override
        public void print() {
            System.out.println("=== " + name  + "===");
            System.out.println("project : " + project);
            System.out.println("oid      : " + id);
            System.out.println("type     : " + type);
            System.out.println("schema : " + (hasSchema ? "registered" : "unavailable"));
            if (!datasets.isEmpty())  {
                System.out.println("datasets :");
                for (Dataset ds : datasets) {
                    System.out.println(" - " + ds.uuid + "<" + ds.url + ">");
                }
            }
            if (url != null) {
                System.out.println("URI    : " + url);
            }
            if (host != null && port != null) {
                System.out.println("service    : TCP/" + host + ":" + port);
            }
        }
    }

    record CorrespondenceDetails(
            String project,
            String name,
            int id,
            List<String> endpoints
    ) implements Dto.CorrLangObjectDetails {

        @Override
        public void print() {
            System.out.println("=== " + name  + "===");
            System.out.println("project : " + project);
            System.out.println("oid      : " + id);
            System.out.println("endpoints :");
            for (String ep : endpoints) {
                System.out.println(" - " + ep);
            }
        }
    }

    record ViewDetails(String project, String name, int id, String correspondence, String endpointType) implements Dto.CorrLangObjectDetails {

        @Override
        public void print() {
            System.out.println("=== " + name  + "===");
            System.out.println("project        : " + project);
            System.out.println("oid            : " + id);
            System.out.println("correspondence : " + correspondence);
            System.out.println("endpoint type  : " + endpointType);
        }
    }


    record CorrLangObjectUpdated(String project, String name, int id, String type) implements Dto {

        @Override
        public void print() {
            System.out.println(type + "/"  + name + "(oid: " + id + ") updated.");
        }
    }

    record CorrSpecMerged(String file, List<Message> errors, List<Message> warnings,  List<Action> actions) implements Dto {
        @Override
        public void print() {
            if (!errors.isEmpty()) {
                System.out.println("There were issues with the specification in '" + file +"':" );
                for (Message error : errors) {
                    System.out.println(error.line + ":" + error.column + " " + error.message);
                }
            }
            if (actions.isEmpty()) {
                System.out.println("NO actionable items");
            } else {
                for (Action action : actions) {
                    System.out.println(action.message);
                }
            }

        }
    }

    record Message(int line, int column, String message) {}

    record Action(String message) {}

    /**
     * Prints the object to standard out.
     */
    void print();
}
