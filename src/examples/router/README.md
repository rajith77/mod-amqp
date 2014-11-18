Router
==============

* In this example the AMQP module provides basic routing for both inbound and outbound messages, based on the mapping provided in the configuration file under *vertx.routing-outbound* and *vertx.routing-inbound*.

##### Outbound
```
"vertx.routing-outbound" : {
			"routes" :{
						"ca-weather" : "amqp://localhost:5672/weather-query",
						"us-weather" : "amqp://localhost:5675/weather-query"
	     			  }
	         
	     }
```
* The module will direct any messages it receives from the *vertx event bus*, for the vertx address *ca-weather* to the AMQP peer at *amqp://localhost:5672/weather-query* and any messages it received for the vertx address *us-weather* to the AMQP peer at *amqp://localhost:5675/weather-query*.

* Note that the configuration lists *ca-weather* and *us-weather* as *vertx.handlers* in the configuration file. During startup, the AMQP module will register handlers with the *vertx event bus* using *ca-weather* and *us-weather* as the handler address.

##### Inbound
```
"vertx.routing-inbound" : {
			"routes" :{
						 "us.weather" : "us-weather-reply",
						 "ca.weather" : "ca-weather-reply"
	     			  }
	         
	     }
```
* The module will direct any messages it receives from an AMQP peer, with the address *us.weather*, to the *vertx event bus* with the address *us-weather-reply*

#### How to run the example
Assumes the following commands are run from the examples/router dir.

1. The example assumes AMQP peers capable of receiving inbound connections is running at *localhost:5672* and *localhost:5675*
   If you change the address, make sure you have the peer running at the correct host:port combination.
   
2. Run the AMQP module.
..* vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -conf router.json -cluster

3. Run TestRouterVerticle.java
..* vertx run java/TestRouterVerticle.java

4. A message, sent by the *TestRouterVerticle* with the address *us.weather* should be received by the AMQP peer at *localhost:5672*. The message sent with *ca.weather* should be received by the AMQP peer at *localhost:5675*.

5. Use an AMQP peer to send a message to *amqp:localhost:5673*, with the **address** set as *us.weather* or *ca.weather*.

6. The message should be received by the *TestRouterVerticle* and print it's contents into *system.out*.
   Note the TestRouterVerticle registers hanlders with the *event bus* using *us-weather-reply* and *ca-weather-reply* as the address.
