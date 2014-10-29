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

import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.engine.Collector;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.message.Message;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;

public class Connection
{
    private final org.apache.qpid.proton.engine.Connection connection;

    private final Transport transport;

    private final org.apache.qpid.proton.engine.Session _session;

    private final NetSocket socket;

    private final Collector _collector;

    private final MessageFactory messageFactory = new MessageFactory();

    private boolean closed = false;

    private final ArrayList<Handler<Void>> disconnectHandlers = new ArrayList<Handler<Void>>();

    private final EventHandler _eventHandler;

    public Connection(NetSocket s, EventHandler handler)
    {
        socket = s;
        socket.dataHandler(new DataHandler());
        socket.drainHandler(new DrainHandler());
        socket.endHandler(new EosHandler());
        _eventHandler = handler;

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
        write();
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
    private void processEvents()
    {
        connection.collect(_collector);
        Event event = _collector.peek();
        while (event != null)
        {
            switch (event.getType())
            {
            case CONNECTION_REMOTE_OPEN:
                 _eventHandler.onConnectionOpen(this);
                break;
            case CONNECTION_FINAL:
                _eventHandler.onConnectionClosed(this);
                break;
            case SESSION_REMOTE_OPEN:
                Session ssn = (Session) event.getSession().getContext();
                _eventHandler.onSessionOpen(ssn);
                break;
            case SESSION_FINAL:
                ssn = (Session) event.getSession().getContext();
                _eventHandler.onSessionClosed(ssn);
                break;
            case LINK_REMOTE_OPEN:
                Link link = event.getLink();
                if (link instanceof Receiver)
                {
                    InboundLink inboundLink = (InboundLink) link.getContext();
                    _eventHandler.onInboundLinkOpen(inboundLink);
                }
                else
                {
                    OutboundLink outboundLink = (OutboundLink) link.getContext();
                    _eventHandler.onOutboundLinkOpen(outboundLink);
                }
                break;
            case LINK_FLOW:
                link = event.getLink();
                if (link instanceof Sender)
                {
                    OutboundLink outboundLink = (OutboundLink) link.getContext();
                    _eventHandler.onOutboundLinkCredit(outboundLink, link.getCredit());
                }
                break;
            case LINK_FINAL:
                link = event.getLink();
                if (link instanceof Receiver)
                {
                    InboundLink inboundLink = (InboundLink) link.getContext();
                    _eventHandler.onInboundLinkClosed(inboundLink);
                }
                else
                {
                    OutboundLink outboundLink = (OutboundLink) link.getContext();
                    _eventHandler.onOutboundLinkClosed(outboundLink);
                }
                break;
            case TRANSPORT:
                // TODO
                break;
            case DELIVERY:
                onDelivery(event.getDelivery());
                break;
            default:
                break;
            }
            _collector.pop();
            event = _collector.peek();
        }
    }

    void onDelivery(Delivery d)
    {
        Link link = d.getLink();
        if (link instanceof Receiver)
        {
            if (d.isPartial())
            {
                return;
            }

            Receiver receiver = (Receiver) link;
            byte[] bytes = new byte[d.pending()];
            int read = receiver.recv(bytes, 0, bytes.length);
            Message pMsg = Proton.message();
            pMsg.decode(bytes, 0, read);
            receiver.advance();

            InboundLink inLink = (InboundLink) link.getContext();
            Session ssn = inLink.getSession();
            AmqpMessage msg = new InboundMessage(ssn.getID(), d.getTag(), ssn.getNextIncommingSequence(),
                    d.isSettled(), pMsg);
            _eventHandler.onMessage(inLink, msg);
        }
        else
        {
            if (d.remotelySettled())
            {
                Tracker tracker = (Tracker) d.getContext();
                tracker.setDisposition(d.getRemoteState());
                tracker.markSettled();
                _eventHandler.onSettled(tracker);
            }
        }
    }
    
    void write()
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
            write();
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