Simple Bridge
==============

In this example the AMQP module simply passes any message it receives to the AMQP endpoint configured via the *amqp.default-outbound-address*.
Similarly any messages it receives will be sent to the event bus with the *address* field in the message as the *event bus address*.

The AMQP module will register a handler with the event bus using the address configured via *vertx.default-handler-address*. The default address is *vertx.mod-amqp*.
Additional handlers can be registered by configuring a list via the optional property *vertx.handlers*.

#### How to run the example
Assumes the following commands are run from the examples/bridge dir.

1. The example assumes an AMQP peer capable of receiving inbound connections is running at localhost:5672
   If you change the address, make sure you have the peer running at the correct host:port combination.
   
2. Run the AMQP module.
..* vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -conf bridge.json -cluster

3. Run TestBridgeVerticle.java
..* vertx run java/TestBridgeVerticle.java

4. A message, sent by the *TestBridgeVerticle* should be received by the AMQP peer.

5. Use an AMQP peer to send a message to *amqp:localhost:5673*, with the address set as *bridge*.

6. The message should be received by the *TestBridgeVerticle* and print it's contents into *system.out*.

7. Notice that the *TestBridgeVerticle* registers a handler with the *vertx event bus* using *bridge* as the address.
