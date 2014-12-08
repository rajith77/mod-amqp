 AMQP 1.0 busmod for Vert.x - Connecting AMQP & Vertx ecosystems.
=================================================================

The vert.x AMQP module provides a bridge between AMQP 1.0 messaging applications & Vertx applications.
As the first phase, the module aims to support the following interaction patterns in both directions.
+ Request/Reply
+ Pub/Sub

![](https://github.com/rajith77/mod-amqp/blob/master/doc/images/vertx-amqp.jpeg)

A Verticle should see no difference between another Verticle and an AMQP node when communicating, while an AMQP node should _see some similarities_ between a Verticle and another AMQP Node. Given Vertx has very basic messaging capabilities, it's difficult for a Verticle to represent all AMQP features. Futher improvements are planned to make the _briding_ as smooth as possible, including dynamic/programatic management of AMQP link.

#### How to run the module
* vertx runmod com.tworlabs~mod-amqp~*version* -conf *config-file* -cluster

For example
* vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -conf router.json -cluster

### Examples and how to run them.
* [Request/Reply example in both directions](https://github.com/rajith77/mod-amqp/tree/master/src/examples/request-reply)
* [Pub/Sub example in both directions](https://github.com/rajith77/mod-amqp/tree/master/src/examples/pub-sub)

### Configuration guide.
Static configuration is specified via a json file at deployment time.
Please check the examples above for sample configuration files.

*Please note all configuration is optional.*

* *amqp.inbound-host*, default *localhost*
  *amqp.inbound-port*, default *5673*
  Specifies the host:port combination for the module to listen on for incomming AMQP connections.

* *amqp.default-outbound-address*, default *amqp://localhost:5672/vertx*
  By default all messages will be sent here, unless the messages matches any additional routing information is specified via *vertx.routing-outbound*

* *vertx.default-handler-address*, default *vertx.mod-amqp*
  Specifies the default address used by the module for receiving messages from the *vertx event-bus*

* *vertx.handlers*, default *[] empty list*.
  Specifies additional handler addresses for receiving messages from the *vertx event-bus*.
  Ex ["ca-weather", "us-weather"]

* *vertx.default-inbound-address*, default *null*
  Specifies the address used when sending incomming messages to the event bus, if the module cannot find the information requested via the configuration. Treat this as a *dead-letter-queue* address.

* *vertx.routing-outbound*
  Provides custom routing information for messages flowing outbound. See *routing-property-name* and *routes*.

* *vertx.routing-inbound*
  Provides custom routing information for messages flowing inbound. See *routing-property-name* and *routes*.

* *routing-property-name* (Nested under *vertx.routing-outbound* or *vertx.routing-inbound*)
  * For outbound, this is a *property-name* that is specified in *properties* or *application-properties* section of the Json message.
  
  * For inbound, this is a *property-name* specified in *application-properties* of the AMQP message.

* *routing-property-type*, allowed values *[ADDRESS, SUBJECT, REPLY_TO, MESSAGE_ID, CORRELATION_ID, LINK_NAME, CUSTOM]* default *ADDRESS* 
  * Please note this is for inbound routing only!
  * If *CUSTOM* is specified, it will use *routing-property-name* to figure out which property to look up.

* *routes* (Nested under *vertx.routing-outbound* or *vertx.routing-inbound*)
  * Provides a routing table for mapping a vertx address to an AMQP address or vice versa depending on the direction.
  * Supports wild card matching.
