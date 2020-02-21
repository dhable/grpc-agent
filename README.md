# grpc-agent

A proof of concept on how to model the existing HTTP & STOMP connections in OpsCenter using a single gRPC
connection instead.

## Goals

* A model where all connections are established from the agent to the opscetnerd server. This eliminates the need
to open inbound ports on DSE/agent boxes (as we have today) which would reduce the network configuration required
to get OpsCenter to run. It also makes the agent more amicable to containered environments where a container may
be handed different IP addresses between starts.

* Reduce the size of the data being streamed. gRPC leverages protocol buffers which would eliminate the overhead of
JSON through the use of agreed upon schemas that don't need to send text keys for every entry. Protobuf 3 is sufficient
to make for clean mappings with our existing payloads.

* Can be leveraged with our existing code bases in Clojure and Jython. Since this is a modern Java solution, it can
interop with both languages well.

* Can fit into the existing model of how the agents and opscenterd communicate. Using a pattern of bidirectional
streams, we can get the verbose back and forth that our current code base is designed around. Longer term if we decide
to correct things further, we can start to rethink how often and when the two components talk with each other on the
network.

* Adopt a model that can support compatability with opscenterd/agent version mismatches. gRPC is built around this
principal if you follow some simple patterns in the code.

## Limitations

* Since the agent is no longer listening for inbound connections, we lose the ability for a script or third party
to invoke an agent action based on a ReST call. Unofficially we've allowed customers and support to do that. I'm not
sure how many actually do.

* Messages are no longer plain text and would require more effort to sniff and inspect on the wire.

* Upon cut over to gRPC, older agent/opscenterd instances will be 100% incompatible. 

## Risks

This is going to be a shift for the team and support. There is a risk that we may have some early stability with the
agent connections as we work through the development process. 

## Runing the Demo

To run the server:
> gradle :server:run --args="--port 12345" -console plain --no-daemon

To run the agent:
> gradle :agent:run --args="--id abc123 --port 12345 --server localhost" -console plain --no-daemon

