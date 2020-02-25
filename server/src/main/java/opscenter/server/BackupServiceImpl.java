package opscenter.server;

import io.grpc.stub.StreamObserver;
import opscenter.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// This represents an implementation of a gRPC service. All rpc methods from the IDL are defined and implemented in
// this class.
public class BackupServiceImpl extends BackupServiceGrpc.BackupServiceImplBase {
    private Logger log = LoggerFactory.getLogger(BackupServiceImpl.class);
    private ScheduledExecutorService mockBackupJobExecutor = Executors.newSingleThreadScheduledExecutor();

    // Implements rpc GetBackupConfiguration (BackupConfigRequest) returns (BackupConfigResponse) {}
    @Override
    public void getBackupConfiguration(BackupConfigRequest request, StreamObserver<BackupConfigResponse> responseObserver) {
        log.info("Backup configuration requested for agent {}", request.getAgentId());
        responseObserver.onNext(
                BackupConfigResponse
                        .newBuilder()
                        .putDestinations("4e93206a-54e4-11ea-a2e3-2e728ce88125",
                                Destination
                                        .newBuilder()
                                        .setAccelerationMode(false)
                                        .setAccessKey("accesskey")
                                        .setAccessSecret("secret")
                                        .setPath("super-cool-bucket-name")
                                        .setProvider(DestinationProvider.S3)
                                        .setServerSideEncryption(false)
                                        .setThrottleBytesPerSecond(0)
                                        .build())
                        .putDestinations("4e93252e-54e4-11ea-a2e3-2e728ce88125",
                                Destination
                                        .newBuilder()
                                        .setProvider(DestinationProvider.LOCAL_DISK)
                                        .setPath("/mnt/nfs/opscenter_backups")
                                        .build()
                                )
                        .build()
        );
        // Notice that you must call onCompleted() in order to indicate the server is done generating data. Since the
        // agent is making a blocking call here, not calling onComplete will cause the agent to hang on startup.
        responseObserver.onCompleted();
    }

    // Implements rpc SnapshotActivity (stream BackupMessageEnvelope) returns (stream BackupMessageEnvelope) {}
    @Override
    public StreamObserver<BackupMessageEnvelope> snapshotActivity(StreamObserver<BackupMessageEnvelope> responseObserver) {
        log.info("Agent opened stream to snapshotActivity");

        // This scheduled executor mimics activity in opscenterd that would request a snapshot from various agents.
        // It does this by placing a new TakeSnapshotRequest protobuf message on the responseObserver stream and
        // waiting for delivery. Notice this code doesn't call onComplete because we never want to close the open
        // streams.
        ScheduledFuture<?> scheduler = mockBackupJobExecutor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        log.info("generating a snapshot request");
                        final BackupMessageEnvelope agentMsg = BackupMessageEnvelope
                                .newBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .setTakeSnapshotReq(
                                        TakeSnapshotRequest
                                                .newBuilder()
                                                .addDestinationId("4e93206a-54e4-11ea-a2e3-2e728ce88125")
                                                .addDestinationId("4e93252e-54e4-11ea-a2e3-2e728ce88125")
                                                .setAt(System.currentTimeMillis() + 10000)
                                                .setTagId(UUID.randomUUID().toString())
                                                .build())
                                .build();
                        responseObserver.onNext(agentMsg);
                    }
                }, 60, 90, TimeUnit.SECONDS);

        // The return stream observer is the code that will be called when messages from the client arrive.
        // This code is a simple dispatch pattern based on the field set in the oneof struct.
        return new StreamObserver<BackupMessageEnvelope>() {
            @Override
            public void onNext(BackupMessageEnvelope value) {
                // The type of field set is encoded as a number and doesn't seem to be a good enum def for it
                // that I've found so far.
                switch(value.getDetailsCase().getNumber()) {
                    case 16: // Err response from the agent
                       log.error("Request {} - Err: {}", value.getOperationId(), value.getErr().getMessage());
                        break;

                    case 17: // Success response from the agent
                        log.info("Request {} - SUCCESS!", value.getOperationId());
                        break;

                    case 18: // TakeSnapshotRequest - opscd should never get this
                        log.error("UNKNOWN RESPONSE - TakeSnapshotRequest");
                        break;

                    case 19: // ListSnapshotRequest - opscd should never get this
                        log.error("UNKNOWN RESPONSE - TakeSnapshotRequest");
                        break;
                }
            }

            @Override
            public void onError(Throwable t) {
                scheduler.cancel(true);
                log.error("onError called");
            }

            @Override
            public void onCompleted() {
                scheduler.cancel(true);
                log.info("Agent connection terminated.");
            }
        };
    }
}
