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
package com.tworlabs.vertx.mod.amqp;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonElement;

public class AmqpClientBusMode extends BusModBase
{
    private AmqpClient client;

    @Override
    public void start()
    {
        super.start();
        String host = getOptionalStringConfig("host", "localhost");
        int port = getOptionalIntConfig("port", 5672);

        String address = getOptionalStringConfig("address", "vertx.mod-amqp");

        client = new AmqpClient(vertx.createNetClient());

        client.connect(port, host, new AsyncResultHandler<Connection>()
        {
            public void handle(AsyncResult<Connection> result)
            {
                if (result.failed())
                {
                    System.out.println("Failed to connect to AMQP peer: " + result.cause());
                }
                else
                {
                    System.out.println("Connected to AMQP peer");
                    setUp();
                }
            }
        });        
    }

    private void setUp()
    {
        
    }
    
    @Override
    public void stop()
    {
        super.stop();
    }

}
