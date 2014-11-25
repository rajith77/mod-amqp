/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class PubVerticle extends Verticle
{
    @Override
    public void start()
    {
        JsonObject msg1 = new JsonObject();
        msg1.putObject("application-properties", new JsonObject().putString("routing-key", "foo.bar"));
        msg1.putString("body", "hello world from foo bar");
        vertx.eventBus().publish("vertx.mod-amqp", msg1);
        System.out.println("Publiser verticle sent msg : " + msg1.encodePrettily());

        JsonObject msg2 = new JsonObject();
        msg2.putObject("application-properties", new JsonObject().putString("routing-key", "foo.baz"));
        msg2.putString("body", "hello world from foo baz");
        vertx.eventBus().publish("vertx.mod-amqp", msg2);
        System.out.println("Publiser verticle sent msg : " + msg2.encodePrettily());
    }
}
