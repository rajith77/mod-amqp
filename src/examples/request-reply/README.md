### Introduction.
This simple example shows how to run the request/reply pattern between Vertx apps & AMQP apps in both directions.
_Reply-to works automagically_, without having to resort to any extra code or configuration.

The Hello Serivce, converts the request to upper case and appends "HELLO : " to it and sends it off as the response.

### How to run the examples.
The tutorial assumes, 
+ You have a basic knowledge of Vertx concepts and how to run/deploy verticles.
For more info please [click here](http://vertx.io/manual.html)

+ You have a basic understanding of [AMQP](www.amqp.org), and have downloaded the proton.jar from [here](http://qpid.apache.org/releases/qpid-proton-0.8/index.html) and have set the "PROTON_LIB" variable to point to it.

+ You have read the general introduction to the AMQP module [here](https://github.com/rajith77/mod-amqp/blob/master/README.md)

#### 1. Vertx Server - AMQP Client
1. Start the AMQP Verticle in clustered mode, with the correct configuration.
   ```
   vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -cluster -conf request-reply.json
   ```

2. Start the Server Verticle in cluster mode.
   Java Version (from src/examples/request-reply/java dir)
   ```
   vertx run ServerVerticle.java -cluster
   ```

   OR the Ruby Version (from src/examples/request-reply/rb dir)
   ```
   vertx run ServerVerticle.rb -cluster
   ```

3. Run the Amqp client app. (from src/examples/request-reply/java dir)
   Note : You need to set "PROTON_LIB" env varible to point to the proton jar. See above.
   ```
   ./runj AmqpClient.java
   ```

4. You should see the AmqpClient print the following.
   ```
   AMQP client sent request : rajith
   AMQP client received response : HELLO RAJITH
   ```

#### How it works.
+ First lets look at the [configuration](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/request-reply.json) snippet below, which defines a mapping between an AMQP address and an Event Bus address.
  ```json
  "vertx.routing-inbound" : {
			"routes" :{
				     "amqp://localhost:5673/hello-service" : "hello-service"
	     			  }
	         
	     } 
  ``` 
+ This tells the AMQP Verticle to forward, any messages it receives at the AMQP address _"amqp://localhost:5673/hello-service"_ , to the Vertx event-bus address _hello-service_.
...This is the _address_ that the Server verticle is listening to. See [line 26 : ServerVerticle.java](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/java/ServerVerticle.java#L26) OR [line 18 : ServerVerticle.rb](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/rb/ServerVerticle.rb#L18)
+ It then sends the response by simply calling _reply_ on the request message. See [line 36 : ServerVerticle.java](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/java/ServerVerticle.java#36) OR [line 22 : ServerVerticle.rb](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/rb/ServerVerticle.rb#L22)
+ The AMQP Verticle will direct the response to the correct AMQP address.

#### 1. AMQP Server - Vertx Client
1. Start the AMQP Verticle in clustered mode, with the correct configuration.
   ```
   vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -cluster -conf request-reply.json
   ```

2. Run the Amqp Server app. (from src/examples/request-reply/java dir)
   Note : You need to set "PROTON_LIB" env varible to point to the proton jar. See above.
   ```
   ./runj AmqpServer.java
   ```

3. Start the Client Verticle in cluster mode.
   Java Version (from src/examples/request-reply/java dir)
   ```
   vertx run ClientVerticle.java -cluster
   ```

4. You should see the ClientVerticle print the following.
   ```json
   Client verticle sent request : {
       "body" : "rajith"
   }
   Succeeded in deploying verticle 
   Client verticle received response : {
   "properties" : {
      "to" : "amqp://localhost:5673/<reply-to>"
   },
   "body" : "HELLO RAJITH",
   "body_type" : "value"
   }
   ```
#### How it works.
+ First lets look at the [configuration](https://github.com/rajith77/mod-amqp/blob/master/src/examples/request-reply/request-reply.json) snippet below, which defines a mapping between an AMQP address and an Event Bus address.
  It also defines a _handler address_ for the AMQP Verticle to register with the Vertx event-bus.
  ```json
  "vertx.handlers" : ["hello-service-amqp"],

  "vertx.routing-outbound" : {
			"routes" :{
				     "hello-service-amqp" : "amqp://localhost:5672/hello-service-amqp"
	     			  }
	         
	     }
  ``` 
+  "vertx.handlers" : ["hello-service-amqp"] tells AMQP Verticle to register a handler for address _hello-service-amqp_ with Vertx event-bus.
    This allows the AMQP Verticle to intercept the request msg sent by the ClientVerticle to the AMQP Service.
    Note : _vertx.handlers_ allows a list of one or more handlers for the AMQPVerticle to register with the event-bus.

+ The _route_ within the _vertx.routing-outbound_ section, tells the AMQP Verticle to forward, any messages it receives at the event-bus address _hello-service-amqp_ to the AMQP address _amqp://localhost:5672/hello-service-amqp_.
...This _AMQP address_ is where the AMQP Server is listening at.

+ It then sends the response by simply calling setting  the _reply-to_ field of the request message, to the _address_ field of the response message, as you would do in a normal AMQP application.

+ The AMQP Verticle will be listening at the _reply-to AMQP address_, and directs the response to the _reply-to-handler_ of the Client Verticle.

