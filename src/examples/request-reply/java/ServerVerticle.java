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
        vertx.eventBus().registerHandler("hello-service", this);
    }

    @Override
    public void handle(Message<JsonObject> requestMsg)
    {
        System.out.println("Server verticle received request : " + requestMsg.body().encodePrettily());

        JsonObject replyMsg = new JsonObject();
        replyMsg.putString("body", "HELLO " + requestMsg.body().getString("body").toUpperCase());
        requestMsg.reply(replyMsg);

        System.out.println("Server verticle sent reply : " + replyMsg.encodePrettily());
    }
}