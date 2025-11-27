package io.corrlang.cli;

import io.corrlang.protocol.Ccp;
import io.corrlang.protocol.Core;
import io.corrlang.protocol.CoreServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.time.Duration;
import java.time.Instant;

public class CoreServiceClient {

    private final CoreServiceGrpc.CoreServiceBlockingStub client;

    private final int port;

    public CoreServiceClient(String host, int port) {
        this.port = port;
        this.client = CoreServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
    }

    public void getStatus() {
        try {
            Core.CorrLangServiceStatus status = this.client.getStatus(Core.GetStatusRequest.newBuilder().build());
            Instant startupTs = Instant.ofEpochSecond(status.getStartupTS());
            Instant now = Instant.now();
            Duration duration = Duration.between(startupTs, now);
            System.out.println("Status      : RUNNING");
            System.out.println("API version : " + status.getApiVersion());
            System.out.println("Service PID : " + status.getPid());
            System.out.println("Service Port: TCP/" + port);
            System.out.println("Startup TS  : " + startupTs.toString());
            System.out.println("Uptime      : " + duration.toString());
        } catch (StatusRuntimeException e) {
            System.out.println("Status      : OFFLINE");
        }
    }

    public void checkConnection() {
        // send a simple request to check if the service is reachable
        this.client.getStatus(Core.GetStatusRequest.newBuilder().build());
    }

    public void shutdownService() {
        this.client.requestShutdown(Core.ShutdownRequest.newBuilder().setReason("CLI Request: PID=" + ProcessHandle.current().pid()).setGracePeriodInS(1).build());
    }

    public void listTechspaces() {

        Core.GetRegisteredTechSpacesResponse techSpaces = this.client.getRegisteredTechSpaces(Core.GetRegisteredTechSpacesRequest.newBuilder().build());
        System.out.println("Available techspaces:");
        for (Ccp.TechSpaceDescription techSpaceInfo : techSpaces.getTechSpacesList()) {
            System.out.println("- " + techSpaceInfo.getName());
        }
        if (techSpaces.getTechSpacesCount() == 0) {
            System.out.println(" <empty> ");
        }
    }

    public void listEndpoints() {
        System.out.println("Registered endpoints:");
        Core.GetRegisteredEndpointsResponse endpoints = this.client.getRegisteredEndpoints(Core.GetRegisteredEndpointsRequest.newBuilder().build());
        for (Ccp.Endpoint endpointInfo : endpoints.getEndpointsList()) {
            System.out.println("- " + endpointInfo.getProject() + "/" + endpointInfo.getName());
        }
        if (endpoints.getEndpointsCount() == 0) {
            System.out.println(" <empty> ");
        }
    }
}
