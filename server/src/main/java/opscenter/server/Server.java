package opscenter.server;

import io.grpc.ServerBuilder;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    public static void main(final String[] args) throws Exception {
        final Options options = new Options();
        options.addRequiredOption("p", "port", true, "Port for the gRPC server to bind to");

        CommandLine cmdLine = null;
        try {
            final CommandLineParser parser = new DefaultParser();
            cmdLine = parser.parse(options, args);
        } catch (ParseException ex) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("gradle :server:run --args=\"...\"", options);
            System.exit(1);
        }

        final int port = Integer.parseInt(cmdLine.getOptionValue("port"));

        // This is where the gRPC server is created and started. At this point, opscenterd would now be
        // listening for agent connections. When a connection is made, inbound requests will be dispatched
        // to various service handlers that have been associated with the server.
        final io.grpc.Server gRPCServer = ServerBuilder.forPort(port)
                .addService(new BackupServiceImpl())
                .build();
        gRPCServer.start();

        log.info("gRPC server listening on port " + port);

        gRPCServer.awaitTermination();

    }

}