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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.net.NetSocket;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Collector;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Endpoint;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Transport;

public class Connection
{
    private final org.apache.qpid.proton.engine.Connection connection;

    private final Transport transport;

    private final org.apache.qpid.proton.engine.Session _session;

    private final NetSocket socket;

    private final Collector _collector;

    private final MessageFactory messageFactory = new MessageFactory();

    private int linkCounter = 0;

    private boolean closed = false;

    private final ArrayList<Handler<Void>> disconnectHandlers = new ArrayList<Handler<Void>>();

    private final CountDownLatch _connectionReady = new CountDownLatch(1);
    
    public Connection(NetSocket s)
    {
        socket = s;
        socket.dataHandler(new DataHandler());
        socket.drainHandler(new DrainHandler());
        socket.endHandler(new EosHandler());
        
        connection = org.apache.qpid.proton.engine.Connection.Factory.create();
        transport = org.apache.qpid.proton.engine.Transport.Factory.create();
        _collector = Collector.Factory.create();
        
        connection.setContext(this);
        connection.collect(_collector);        
        transport.bind(connection);
        _session = connection.session();
    }

    void addDisconnectHandler(Handler<Void> handler)
    {
        disconnectHandlers.add(handler);
    }

    public void open()
    {
        connection.open();
        _session.open();
        doOutput();
        try
        {
            _connectionReady.await();
        }
        catch (InterruptedException e)
        {
            //
        }
    }

    public boolean isOpen()
    {
        return !closed && connection.getLocalState() == EndpointState.ACTIVE
                && connection.getRemoteState() == EndpointState.ACTIVE;
    }

    public void close()
    {
        connection.close();
    }

    public Sender createSender(String address)
    {
        String name = address + "_" + (++linkCounter);
        org.apache.qpid.proton.engine.Sender l = sessionFactory.get().sender(name);
        Target t = new Target();
        t.setAddress(address);
        l.setTarget(t);
        Sender s = new Sender(l, messageFactory);
        l.setContext(s);
        return s;
    }

    private Receiver createReceiver(String name, Source source)
    {
        org.apache.qpid.proton.engine.Receiver l = sessionFactory.get().receiver(name);
        l.setSource(source);
        Receiver r = new Receiver(l, messageFactory);
        l.setContext(r);
        return r;
    }

    public Receiver createReceiver(String address)
    {
        String name = address + "_" + (++linkCounter);
        Source source = new Source();
        source.setAddress(address);
        // TODO: support for filter
        return createReceiver(name, source);
    }

    public Receiver createReceiver()
    {
        String name = "dynamic_" + (++linkCounter);
        Source source = new Source();
        source.setDynamic(true);
        return createReceiver(name, source);
    }

    private void linkStateChanged(Link l)
    {
        if (l instanceof org.apache.qpid.proton.engine.Sender)
        {
            sndHandler.changed(l);
        }
        else
        {
            rcvHandler.changed(l);
        }
    }

    private void handleDeliveryEvent(Delivery d)
    {
        Link l = d.getLink();
        if (d.isReadable())
        {
            ((Receiver) l.getContext()).recv(d);
        }
        else if (d.isUpdated())
        {
            boolean isSender = l instanceof org.apache.qpid.proton.engine.Sender;
            // TODO
            if (d.isSettled())
            {
                // incoming message settled
                // outgoing message settled
            }
            else
            {
                // incoming message state changed (but not settled)
                // outgoing message state changed (but not settled)
            }
        }
        else if (d.isWritable())
        {
            // ???
        }
        else
        {
            // ???
        }
    }

    private void processEvents()
    {
        connection.collect(_collector);
        Event e = _collector.peek();
        while (e != null)
        {
            switch (e.getType())
            {
            case CONNECTION_REMOTE_STATE:
                conHandler.changed(connection);
                break;
            case SESSION_REMOTE_STATE:
                ssnHandler.changed(e.getSession());
                break;
            case LINK_REMOTE_STATE:
                linkStateChanged(e.getLink());
                break;
            case DELIVERY:
                handleDeliveryEvent(e.getDelivery());
                break;
            case LINK_FLOW:
                // ?? need to indicate in some way when there is credit to send
                break;
            case TRANSPORT:
                doOutput();
                break;
            case CONNECTION_LOCAL_STATE:
            case SESSION_LOCAL_STATE:
            case LINK_LOCAL_STATE:
                // ignore
                break;
            }
            _collector.pop();
            e = _collector.peek();
        }
    }

    void doOutput()
    {
        if (socket.writeQueueFull())
        {
            System.out.println("Socket buffers are full for " + this);
        }
        else
        {
            ByteBuffer b = transport.getOutputBuffer();
            while (b.remaining() > 0)
            {
                // TODO: what is the optimal solution here?
                byte[] data = new byte[b.remaining()];
                b.get(data);
                socket.write(new Buffer(data));
                transport.outputConsumed();
                b = transport.getOutputBuffer();
            }
        }
    }

    private class DataHandler implements Handler<Buffer>
    {
        public void handle(Buffer data)
        {
            byte[] bytes = data.getBytes();
            int start = 0;
            while (start < bytes.length)
            {
                int count = Math.min(transport.getInputBuffer().remaining(), bytes.length - start);
                transport.getInputBuffer().put(bytes, start, count);
                start += count;
                transport.processInput();
                processEvents();
            }
        }
    }

    private class DrainHandler implements Handler<Void>
    {
        public void handle(Void v)
        {
            doOutput();
        }
    }

    private class EosHandler implements Handler<Void>
    {
        public void handle(Void v)
        {
            closed = true;
            for (Handler<Void> h : disconnectHandlers)
            {
                h.handle(v);
            }
        }
    }
}
