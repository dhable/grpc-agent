package opscenter.agent;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import opscenter.protocol.BackupConfigRequest;
import opscenter.protocol.BackupConfigResponse;
import opscenter.protocol.BackupServiceGrpc;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Agent {
    private final static Logger log = LoggerFactory.getLogger(Agent.class);

    public static void main(final String[] args) {
        final Options options = new Options();
        options.addRequiredOption("s", "server", true, "DNS or IP address of gRPC server");
        options.addRequiredOption("p", "port", true,"Port the gRPC server is listening on");
        options.addRequiredOption("i", "id", true, "Identity GUID for this client");

        CommandLine cmdLine = null;
        try {
            final CommandLineParser parser = new DefaultParser();
            cmdLine = parser.parse(options, args);
        } catch (ParseException ex) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("gradle :agent:run --args=\"...\"", options);
            System.exit(1);
        }

        final String id = cmdLine.getOptionValue("id");
        final String serverAddr = cmdLine.getOptionValue("server");
        final int port = Integer.parseInt(cmdLine.getOptionValue("port"));

        // The gRPC channel is an anbstraction over some underlying network protocol/socket combination. The
        // important part is that this can be shared across multiple components. This means each component only
        // needs to have code related to a subsection of functions while using a shared network connection.
        final ManagedChannel channel =
                ManagedChannelBuilder.forAddress(serverAddr, port)
                                     .userAgent("OpsCenter Agent/7.0.0")
                                     .usePlaintext()
                                     .build();

        // These represent the generated Java code from gRPC as it relates to this individual service.
        final BackupServiceGrpc.BackupServiceBlockingStub blockingStub = BackupServiceGrpc.newBlockingStub(channel);
        final BackupServiceGrpc.BackupServiceStub asyncStub = BackupServiceGrpc.newStub(channel);

        // As part of component startup, each component is allowed to ask the opscenterd server for the
        // configuration that relates only to its part of the universe. Since nothing can happen without
        // config, a blocking method is acceptable here.
        BackupConfigResponse configResponse = blockingStub.getBackupConfiguration(
                BackupConfigRequest.newBuilder()
                        .setAgentId(id)
                        .build());

        log.info("Agent configuration received - {}", configResponse.toString());

        // This is how we achieve inverting the client/server roles and yet still allowing requests to
        // be initiated from opscenterd. By doing this we make the code look less like it's just making function
        // calls. With more effort, we could simply make the gRPC interface polling based where the agent would
        // call functions looking for work to do on a fixed basis.
        BackupComponent dispatcher = new BackupComponent(asyncStub);
        dispatcher.connect();

        try {
            while (true) {
                log.debug("main thread sleep");
                Thread.sleep(10000);
            }
        } catch (InterruptedException ex) {
            log.info("shutting down");
        }
    }

}