Router
==============

* This example builds on the router example and configures the AMQP module to provide a more custom approach for routing for both inbound and outbound messages, based on the mapping provided in the configuration file under *vertx.routing-outbound* and *vertx.routing-inbound*.

##### Outbound
```
"vertx.routing-outbound" : {
			"routing-property-name" : "routing-key", 
			"routes" :{
						"ca.*" : "amqp://localhost:5672/weather-query",
						"us.*" : "amqp://localhost:5675/weather-query"
	     			  }
	         
	     }
```
* The configuration lists *ca-weather* and *us-weather* as *vertx.handlers* in the configuration file. During startup, the AMQP module will register handlers with the *vertx event bus* using *ca-weather* and *us-weather* as the handler address.

* The module will look at the *routing-key* property in the messages it receives from the event bus and will do a wild-card match to determine the outbound AMQP peer.

* For example, if it receives a message from the *vertx event bus* with the *routing-key* property set to *us.ny.nyc* or *us.ma.bos* , it will be sent to the AMQP peer at *amqp://localhost:5672/weather-query*


##### Inbound
```
"vertx.routing-inbound" : {
			"routing-property-name" : SUBJECT, 
			"routes" :{
						 "us.*" : "us-weather-reply",
						 "ca.*" : "ca-weather-reply"
	     			  }
	         
	     } 
```
* The module will direct any messages it receives from an AMQP peer, with the *SUBJECT* set to *us.ny.nyc-reply* or *us.ma.bos-reply*, to the *vertx event bus* with the address *us-weather-reply*

#### How to run the example
Assumes the following commands are run from the examples/router dir.

1. The example assumes AMQP peers capable of receiving inbound connections is running at *localhost:5672* and *localhost:5675*
   If you change the address, make sure you have the peer running at the correct host:port combination.
   
2. Run the AMQP module.
..* vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -conf router.json -cluster

3. Run TestRouterVerticle.java
..* vertx run java/TestCustomRouterVerticle.java

4. A message, sent by the *TestCustomRouterVerticle* with the routing-key *us.ny.nyc* should be received by the AMQP peer at *localhost:5672*. The message sent with *ca.on.yyz* should be received by the AMQP peer at *localhost:5675*.

5. Use an AMQP peer to send a message to *amqp:localhost:5673*, with the *subject* set as *us.ny.nyc-reply* or *ca.on-reply*.

6. The message should be received by the *TestCustomRouterVerticle* and print it's contents into *system.out*.
   Note the TestCustomRouterVerticle registers hanlders with the *event bus* using *us-weather-reply* and *ca-weather-reply* as the address.
