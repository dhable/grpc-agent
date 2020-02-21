package opscenter.agent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.grpc.stub.StreamObserver;
import opscenter.protocol.BackupMessageEnvelope;
import opscenter.protocol.BackupServiceGrpc;
import opscenter.protocol.Common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This maps closely to our Clojure components and we
// should probably think of a Component as implementing a pattern like this.
public class BackupComponent implements StreamObserver<BackupMessageEnvelope> {
    private static final Logger log = LoggerFactory.getLogger(BackupComponent.class);
    private final BackupServiceGrpc.BackupServiceStub asyncStub;
    private StreamObserver<BackupMessageEnvelope> outboundMessageStream = null;
    private Cache<String, Thread> longRunningOps = CacheBuilder.newBuilder().maximumSize(600).build();

    // Needs the asyncStub gRPC client so it can setup the bidirectional stream with the server.
    public BackupComponent(BackupServiceGrpc.BackupServiceStub asyncStub) {
        this.asyncStub = asyncStub;
    }

    @Override
    public void onNext(BackupMessageEnvelope value) {
        // This is a simple dispatch block of code based on the details payload in the oneof structure.
        final String opId = value.getOperationId();
        switch (value.getDetailsCase().getNumber()) {
            case 16: // Err - the agent should never get this value
                log.error("Agent got unexpected Err message");
                break;

            case 17: // Success - the agent should never get this value
                log.error("Agent got unexpected Success message");
                break;

            case 18: // TakeSnapshotRequest
                log.error("TakeSnapshotRequest id: {}", opId);
                // This delayed thread mimics a longer running activity.
                final Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(20000);
                            log.info("Snapshot {} being returned as success", opId);

                            // When the agent has finished, it can simply put a return type on the outbound stream
                            // that was established when the component started up.
                            BackupMessageEnvelope retEnvlop = BackupMessageEnvelope
                                    .newBuilder()
                                    .setOperationId(opId)
                                    .setOk(Common.Success.newBuilder().build())
                                    .build();
                            outboundMessageStream.onNext(retEnvlop);
                            // Notice there is no need to call onComplete() since this component will listen for more
                            // requests
                        } catch (InterruptedException ex) { }
                    }
                };
                t.start();
                longRunningOps.put(opId, t);
                break;

            // Additional cases here
        }

    }

    @Override
    public void onError(Throwable t) {
        log.error("Agent got an error");
    }

    @Override
    public void onCompleted() {
        // This is called when the connection terminates. Since the agent is a long lived process, it
        // just needs to keep retrying to connect.
        log.error("Connection to server closed");
        outboundMessageStream = null;
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) { }
        connect();
    }

    public void connect() {
        log.info("connecting to snapshotActivity");
        if (outboundMessageStream == null) {
            // The rpc call here is what actually opens and starts the bidirectional stream with the
            // server.
            outboundMessageStream = asyncStub.snapshotActivity(this);
        }
    }

}

