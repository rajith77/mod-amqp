### Introduction.
This simple example shows how to run the pub/sub pattern between Vertx apps & AMQP apps in both directions.

The subscriber _Sub1_ subscribes to the topic _foo.*_ and _Sub2_ subscribes to the topic _foo.bar.*_.
The publisher sends to messages. One to _foo.bar_ and one to _foo.baz_.
_Sub1_ should get both messages and _Sub2_ should only get one message.

### How to run the examples.
The tutorial assumes, 
+ you have a basic knowledge of Vertx concepts and how to run/deploy verticles.
For more info please [click here](http://vertx.io/manual.html)

+ You have a basic understanding of [AMQP](www.amqp.org), and have downloaded the proton.jar from [here](http://qpid.apache.org/releases/qpid-proton-0.8/index.html) and have set the "PROTON_LIB" variable to point to it.

+ You have read the general introduction to the AMQP module [here](https://github.com/rajith77/mod-amqp/blob/master/README.md)

#### 1. Vertx Pub - AMQP Subscribers
1. Start the AMQP Verticle in clustered mode, with the correct configuration.
   ```
   vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -cluster -conf pub-sub.json
   ```

3. Run the two Amqp subscriber apps. (from src/examples/pub-sub/java dir)
   Note : You need to set "PROTON_LIB" env varible to point to the proton jar. See above.
   ```
   ./runj AmqpSub1.java
   ./runj AmqpSub2.java
   ```

2. Start the Publisher Verticle in cluster mode.
   Java Version (from src/examples/pub-sub/java dir)
   ```
   vertx run PubVerticle.java -cluster
   ```
   
   OR the JavaScript Version (from src/examples/pub-sub/js dir)
   ```
   vertx run PubVerticle.js -cluster
   ```

4. You should see the AmqpSub1 print the following.
   ```
   Subscribing to topic foo.*
   AMQP subscriber received message : hello world from foo bar
   AMQP subscriber received message : hello world from foo baz
   ```
   And the AmqpSub2 print the following
   ```
   Subscribing to topic foo.bar.*
   AMQP subscriber received message : hello world from foo bar
   ```

#### How it works.
_Vertx 2.0 does not support **wildcard addresses**, therefore a workaround is needed to get around that. Vertx 3.0 will be adding wildcard support which would make the this example work without any sort of configuration._

+ First lets look at the [configuration](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/pub-sub.json) snippet below, which tells the AMQP Verticle to look at the _routing-key_ property in the message when deciding which AMQP address it should use when forwarding the message.
  ```json
  "vertx.routing-outbound" : {
                        "routing-property-name" : "routing-key"	
	     }
  ``` 
+ When the AMQP subscribers create a subscription with the AMQPVerticle, it will dynamically add a mapping between the topics and the subscriptions.

+ When the Publisher verticle sends a message to the AMQP Verticle using it's default address _'vertx.mod-amqp'_ - "See [line 20 : PublisherVerticle.js](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/js/PubVerticle.js#L20) & [line 25 : PublisherVerticle.js](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/js/PubVerticle.js#L25) ", it will use the value given in the _routing-key_ property to match it to the correct AMQP Subscription.

#### 1. AMQP Pub - Vertx Subscribers
1. Start the AMQP Verticle in clustered mode, with the correct configuration.
   ```
   vertx runmod com.tworlabs~mod-amqp~1.0-SNAPSHOT -cluster -conf pub-sub.json
   ```

3. Run the two Vertx subscriber Verticles in cluster mode. (from src/examples/pub-sub/java dir)
   Java Version (from src/examples/pub-sub/java dir)
   ```
   vertx run SubVerticle1.java -cluster
   vertx run SubVerticle2.java -cluster
   ```
   
   OR the Python Version (from src/examples/pub-sub/py dir)
   ```
   vertx run SubVerticle1.py -cluster
   vertx run SubVerticle2.py -cluster
   ```

2. Start the AMQP Publisher. (from src/examples/pub-sub/java dir)
   
   Note : You need to set "PROTON_LIB" env varible to point to the proton jar. See above.
   ```
   ./runj AmqpPub.java
   ```

4. You should see the SubVerticle1 print the following.
   ```
   Subscriber verticle received msg : hello world from foo bar
   Subscriber verticle received msg : hello world from foo baz
   ```
   And the SubVerticle2 print the following
   ```
   Subscriber verticle received msg : hello world from foo bar
   ```

#### How it works.
_Vertx 2.0 does not support **wildcard addresses**, therefore a workaround is needed to get around that. Vertx 3.0 will be adding wildcard support which would make the this example work without any sort of configuration._

+ First lets look at the [configuration](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/pub-sub.json) snippet below, which tells the AMQP Verticle to forward any messages that it receives from an AMQP peer to topic _foo.*_ to vertx address _foo-all_ & _foo.bar.*_ to vertx address _foo-bar_.
  ```json
  "vertx.routing-inbound" : {
			"routes" :{
				     "amqp://localhost:5673/foo.*" : "foo-all",
                                     "amqp://localhost:5673/foo.bar*" : "foo-bar"
	     			  }
	         
	     } 
  ``` 

+ The subscriber Verticles will register handlers for _foo-all_ and _foo-bar_ - "See [line 21 : SubVerticle1.py](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/py/SubVerticle1.py#L21) & [line 21 : SubVerticle2.py](https://github.com/rajith77/mod-amqp/blob/master/src/examples/pub-sub/py/SubVerticle2.py#L21) ".
