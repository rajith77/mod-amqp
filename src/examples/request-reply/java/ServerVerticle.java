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
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class ServerVerticle extends Verticle implements Handler<Message<JsonObject>>
{
    @Override
    public void start()
    {
        vertx.eventBus().registerHandler("server-verticle", this);
    }

    @Override
    public void handle(Message<JsonObject> msg)
    {
        System.out.println("Server verticle received request : " + msg.body().encodePrettily());

        JsonObject m = new JsonObject();
        m.putString("body", msg.body().getString("body").toUpperCase());
        msg.reply(m);

        System.out.println("Server verticle sent reply : " + m.encodePrettily());
    }
}