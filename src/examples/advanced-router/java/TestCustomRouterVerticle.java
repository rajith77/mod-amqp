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
import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class TestCustomRouterVerticle extends Verticle implements Handler<Message<JsonObject>>
{

    @Override
    public void start()
    {
        super.start();
        container.logger().info("-------- Test Custom Router Verticle --------");
        vertx.eventBus().registerHandler("us-weather-reply", this);
        vertx.eventBus().registerHandler("ca-weather-reply", this);

        JsonObject m = new JsonObject();
        m.putString("body", "Hello from Vertx");
        Map<String, String> props = new HashMap<String, String>();
        props.put("routing-key", "us.ny.nyc");
        m.putValue("application_properties", props);
        vertx.eventBus().send("us-weather", m);

        m = new JsonObject();
        m.putString("body", "Hello from Vertx");
        props = new HashMap<String, String>();
        props.put("routing-key", "us.ma.bos");
        m.putValue("application_properties", props);
        vertx.eventBus().send("us-weather", m);

        m = new JsonObject();
        m.putString("body", "Hello from Vertx");
        props = new HashMap<String, String>();
        props.put("routing-key", "ca.on.yyz");
        m.putValue("application_properties", props);
        vertx.eventBus().send("ca-weather", m);
    }

    @Override
    public void handle(Message<JsonObject> m)
    {
        StringBuilder b = new StringBuilder();
        b.append("message : ").append(m.body());
    }
}