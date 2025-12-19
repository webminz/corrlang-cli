package io.corrlang.cli;

import com.google.protobuf.ByteString;
import io.corrlang.protocol.Ccp;
import io.corrlang.protocol.Core;
import io.corrlang.protocol.CoreServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CoreServiceClient {

    private final CoreServiceGrpc.CoreServiceBlockingStub client;

    private final int port;

    public CoreServiceClient(String host, int port) {
        this.port = port;
        this.client = CoreServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
    }

    public Dto.CorrLangServiceStatus getStatus() {
        try {
            Core.CorrLangServiceStatus status = this.client.getStatus(Core.GetStatusRequest.newBuilder().build());
            return new Dto.CorrLangServiceStatus(
                    true,
                    status.getApiVersion(),
                    status.getPid(),
                    port,
                    Instant.ofEpochSecond(status.getStartupTS())
            );
        } catch (StatusRuntimeException e) {
            return new Dto.CorrLangServiceStatus(false, null, null, port, null);
        }
    }

    public boolean checkConnection() {
        // send a simple request to check if the service is reachable
        try {
            Core.CorrLangServiceStatus status = this.client.getStatus(Core.GetStatusRequest.newBuilder().build());
            return status.getStartupTS() > 0;
        } catch (StatusRuntimeException e) {
            return false;
        }

    }

    public void shutdownService() {
        this.client.requestShutdown(Core.ShutdownRequest.newBuilder().setReason("CLI Request: PID=" + ProcessHandle.current().pid()).setGracePeriodInS(1).build());
    }

    public Dto.CorrLangTechSpaces listTechspaces() {
        Core.GetRegisteredTechSpacesResponse techSpaces = this.client.getRegisteredTechSpaces(Core.GetRegisteredTechSpacesRequest.newBuilder().build());
        return new Dto.CorrLangTechSpaces(techSpaces.getTechSpacesList().stream().map(Ccp.TechSpaceDescription::getName).toList());
    }



    public Dto.TechSpaceDetails getTechSpaceInfo(String techSpaceName) {
        Core.GetRegisteredTechSpacesResponse techSpaces = this.client.getRegisteredTechSpaces(Core.GetRegisteredTechSpacesRequest.newBuilder().build());
        for (Ccp.TechSpaceDescription techSpace : techSpaces.getTechSpacesList()) {
            if (techSpace.getName().equals(techSpaceName)) {
                return new Dto.TechSpaceDetails(
                        techSpace.getName(),
                        techSpace.getDescription(),
                        techSpace.getDeveloper(),
                        techSpace.getCapabilitiesList().stream().map(Ccp.TechSpaceCapability::name).collect(Collectors.toList())
                );
            }
        }
        throw new IllegalArgumentException("Techspace '" + techSpaceName + "' not found!");
    }

    public Dto.EndpointDetails getEndpointInfo(int endpoint) {
        Ccp.Endpoint result = client.getEndpoint(Core.GetEndpointRequest.newBuilder()
                .setEndpointId(endpoint).build());

        return new Dto.EndpointDetails(
                result.getProject(),
                result.getName(),
                result.getId(),
                result.getType().name(),
                result.hasSchemaRegistered() && result.getSchemaRegistered(),
                result.getDatasetsList().stream().map(ds -> new Dto.Dataset(ds.getUri(), toUUIDString(ds.getUuid()))).toList(),
                result.hasServiceAddress() ? (result.getServiceAddress().hasUrl() ? result.getServiceAddress().getUrl() : null ) : null,
                result.hasServiceAddress() ? result.getServiceAddress().getHostname() : null,
                result.hasServiceAddress() ? result.getServiceAddress().getPort() : null);


    }

    private String toUUIDString(ByteString uuid) {
        byte[] bytes = new byte[16];
        uuid.copyTo(bytes, 0);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong(8)).toString();
    }


    public Dto.CorrespondenceDetails getCorrespondenceInfo(int correspondence) {
        Ccp.Correspondence corr = client.getCorrespondence(Core.GetCorrespondenceRequest.newBuilder()
                .setCorrespondenceId(correspondence)
                .build());
        List<String> endpointNames = new ArrayList<>();
        for (Integer eid : corr.getEndpointsList()) {
            endpointNames.add(client.getEndpoint(Core.GetEndpointRequest.newBuilder().setEndpointId(eid).build()).getName());
        }
        return new Dto.CorrespondenceDetails(
                corr.getProject(),
                corr.getName(),
                corr.getId(),
                endpointNames
        );
    }

    public Dto.ViewDetails getViewInfo(int view) {
        Ccp.View viewObject = client.getView(Core.GetViewRequest.newBuilder().setViewId(view).build());
        String corrName  = client.getCorrespondence(Core.GetCorrespondenceRequest.newBuilder().setCorrespondenceId(viewObject.getCorrespondence()).build()).getName();
        return new Dto.ViewDetails(
                viewObject.getProject(),
                viewObject.getName(),
                viewObject.getId(),
                corrName,
                viewObject.getType().name()
        );
    }

    public Dto.CorrSpecMerged applyCorrSpec(String project, Path corrSpecPath) {
        Ccp.Ack ack = client.mergeCorrSpec(Core.MergeCorrSpecRequest.newBuilder()
                .setProject(project)
                .setCorrSpecFilePath(corrSpecPath.toAbsolutePath().toString())
                .build());
        return new Dto.CorrSpecMerged(corrSpecPath.toAbsolutePath().toString());
    }

    public Dto.CorrLangObjectCreated applyAddEndpoint(String projectName, String endpointName, String endpointType) {
        Ccp.EndpointType t = switch (endpointType) {
            case "dataset" -> Ccp.EndpointType.DATA;
            case "service" -> Ccp.EndpointType.SERVICE;
            case "sink" -> Ccp.EndpointType.SINK;
            case "source" -> Ccp.EndpointType.SOURCE;
            default -> throw new IllegalArgumentException("Unknown endpoint type: " + endpointType);
        };
        Ccp.Endpoint reg = client.registerEndpoint(Core.RegisterEndpointRequest.newBuilder()
                .setName(endpointName)
                .setProject(projectName)
                .setType(t)
                .build());
        return new Dto.CorrLangObjectCreated(projectName, endpointName, reg.getId(), "endpoint");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointSchema(int endpoint, String techSpaceName, File schemaFile) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Ack ack = client.registerEndpointSchema(Core.RegisterEndpointSchemaRequest.newBuilder()
                        .setEndpointId(endpoint)
                        .setTechSpace(techSpaceName)
                        .setFileLocation(schemaFile.getAbsolutePath())
                        .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointSchemaURL(int endpoint, String techSpaceName, String schemaURL) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Ack ack = client.registerEndpointSchema(Core.RegisterEndpointSchemaRequest.newBuilder()
                        .setEndpointId(endpoint)
                        .setTechSpace(techSpaceName)
                        .setUrl(schemaURL)
                        .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointData(int endpoint, String techSpaceName, File dataPath) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Dataset dataset = client.registerEndpointDataset(
                        Core.RegisterEndpointDatasetRequest.newBuilder()
                                .setFileLocation(dataPath.getAbsolutePath())
                                .setTechSpace(techSpaceName)
                                .setEndpointId(endpoint)
                                .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointDataURL(int endpoint, String techSpaceName, String url) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Dataset dataset = client.registerEndpointDataset(
                        Core.RegisterEndpointDatasetRequest.newBuilder()
                                .setUrl(url)
                                .setTechSpace(techSpaceName)
                                .setEndpointId(endpoint)
                                .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointServiceURL(int endpoint, String techSpaceName, String serviceURL) throws URISyntaxException, MalformedURLException {
        URL url = new URI(serviceURL).toURL();
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Ack ack = client.registerEndpointServiceAddress(Core.RegisterEndpointServiceAddressRequest.newBuilder()
                        .setServiceAddress(Ccp.ServiceAddress.newBuilder()
                                .setUrl(serviceURL)
                                .setPort(url.getPort())
                                .setHostname(url.getHost())
                                .setIsUdp(false)
                                .setIsTls(url.getProtocol().startsWith("https"))
                        )
                        .setEndpointId(endpoint)
                        .setTechSpace(techSpaceName)
                        .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangObjectUpdated applyAddEndpointSocket(int endpoint, String techSpaceName, String host, int port) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT).build()).getObjectsList()) {
            if (o.getId() == endpoint) {
                Ccp.Ack ack = client.registerEndpointServiceAddress(Core.RegisterEndpointServiceAddressRequest.newBuilder()
                        .setServiceAddress(
                                Ccp.ServiceAddress.newBuilder()
                                                .setIsUdp(false).setIsTls(false).setHostname(host).setPort(port)
                                .build())
                        .setEndpointId(endpoint)
                        .setTechSpace(techSpaceName)
                        .build());
                return new Dto.CorrLangObjectUpdated(
                        o.getProject(),
                        o.getName(),
                        o.getId(),
                        "endpoint"
                );
            }
        }
        throw new IllegalArgumentException("Endpoint with oid:'" + endpoint + "' not found!");
    }

    public Dto.CorrLangSchemaExported exportEndpointSchema(String project, String endpoint, String techSpace, Path targetFile) {
        for (Ccp.CorrLangObject o : client.getObjects(Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT)
                .setProject(project).build()).getObjectsList()) {
            if (o.getName().equals(endpoint)) {
                int endpointId = o.getId();
                Ccp.Ack ack = client.writeSchema(Core.WriteSchemaRequest.newBuilder()
                        .setEndpointId(endpointId)
                        .setTechSpace(techSpace)
                        .setFileLocation(targetFile.toAbsolutePath().toString()).build()
                );
                return new Dto.CorrLangSchemaExported(targetFile.toAbsolutePath().toString());

            }
        }
        throw new IllegalArgumentException("Endpoint '" + endpoint + "' not found in project '" + project + "'!");
    }

    public Optional<Integer> getEndpointId(String project, String endpoint) {
        Core.GetObjectsRequest.Builder request = Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.ENDPOINT);
        if (project != null) {
            request.setProject(project);
        }
        Core.GetObjectsResponse endpoints = this.client.getObjects(request.build());
        for (Ccp.CorrLangObject o : endpoints.getObjectsList()) {
            if (o.getName().equals(endpoint)) {
                return Optional.of(o.getId());
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> getCorrespondenceId(String project, String correspondence) {
        Core.GetObjectsRequest.Builder request = Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.CORRESPONDENCE);
        if (project != null) {
            request.setProject(project);
        }
        Core.GetObjectsResponse endpoints = this.client.getObjects(request.build());
        for (Ccp.CorrLangObject o : endpoints.getObjectsList()) {
            if (o.getName().equals(correspondence)) {
                return Optional.of(o.getId());
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> getViewId(String project, String view) {
        Core.GetObjectsRequest.Builder request = Core.GetObjectsRequest.newBuilder()
                .setObjectType(Ccp.CorrLangObjectType.VIEW);
        if (project != null) {
            request.setProject(project);
        }
        Core.GetObjectsResponse endpoints = this.client.getObjects(request.build());
        for (Ccp.CorrLangObject o : endpoints.getObjectsList()) {
            if (o.getName().equals(view)) {
                return Optional.of(o.getId());
            }
        }
        return Optional.empty();
    }

    public Dto.CorrLangObjects listAll(String project) {
        Core.GetObjectsRequest.Builder builder = Core.GetObjectsRequest.newBuilder();
        if (project != null) {
            builder.setProject(project);
        }
        Core.GetObjectsResponse response = this.client.getObjects(builder.build());
        return new Dto.CorrLangObjects(response.getObjectsList().stream().map(ob ->
                new Dto.CorrLangObject(
                        ob.getObjectType().name().toLowerCase(),
                        ob.getProject(),
                        ob.getName(),
                        ob.getId()
                )).toList());
    }

    public void removeEndpoint(int eid) {
        client.deregisterObject(Core.DeregisterObjectRequest.newBuilder()
                .setObjectId(eid).build());
    }
}
