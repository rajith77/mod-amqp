/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.tworlabs.vertx.mod.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.splash.messaging.Action;
import org.splash.messaging.CreditMode;
import org.splash.messaging.InboundLink;
import org.splash.messaging.InboundLinkMode;
import org.splash.messaging.Message;
import org.splash.messaging.MessageDisposition;
import org.splash.messaging.MessageFormatException;
import org.splash.messaging.MessagingException;
import org.splash.messaging.NetworkException;
import org.splash.messaging.OutboundLink;
import org.splash.messaging.OutboundLinkMode;
import org.splash.messaging.ReasonCode;

class Session extends ContextAwareImpl implements org.splash.messaging.Session, Action
{
    private static final DeliveryState ACCEPTED = Accepted.getInstance();

    private static final DeliveryState REJECTED = new Rejected();

    private static final DeliveryState RELEASED = Released.getInstance();

    private BaseConnection _conn;

    private Session _ssn;

    private AtomicBoolean _closed = new AtomicBoolean(false);

    private final Map<Link, BaseLink> _links = new HashMap<Link, BaseLink>();

    private final AtomicLong _deliveryTag = new AtomicLong(0);

    private final AtomicLong _incommingSequence = new AtomicLong(0);

    private final Map<Long, Delivery> _unsettled = new ConcurrentHashMap<Long, Delivery>();

    private final AtomicLong _lastSettled = new AtomicLong(0);

    private final AtomicLong _lastDispositionMark = new AtomicLong(0);

    private final String _id;

    Session(BaseConnection conn, Session ssn)
    {
        _id = UUID.randomUUID().toString();
        _conn = conn;
        _ssn = ssn;
    }

    void init() throws NetworkException
    {
        _ssn.open();
        _conn.write();
    }

    @Override
    public void accept() throws NetworkException
    {
        init();
    }

    @Override
    public void reject(ReasonCode code, String desc, String alternateAddress)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public OutboundLink createOutboundLink(String address, OutboundLinkMode mode) throws NetworkException,
            MessagingException
    {
        checkClosed();
        Sender sender;
        Source source = new Source();
        Target target = new Target();
        if (address == null || address.isEmpty() || address.equals("#"))
        {
            String temp = UUID.randomUUID().toString();
            sender = _ssn.sender(temp);
            target.setDynamic(true);
        }
        else
        {
            sender = _ssn.sender(address);
            target.setAddress(address);
        }
        sender.setTarget(target);
        sender.setSource(source);
        sender.setSenderSettleMode(mode == OutboundLinkMode.AT_MOST_ONCE ? SenderSettleMode.SETTLED
                : SenderSettleMode.UNSETTLED);
        sender.open();

        OutboundLink outLink = new OutboundLink(this, address, sender);
        outLink.setDynamicAddress(target.getDynamic());
        _links.put(sender, outLink);
        sender.setContext(outLink);
        _conn.write();
        return outLink;
    }

    @Override
    public InboundLink createInboundLink(String address, InboundLinkMode mode, CreditMode creditMode)
            throws NetworkException, MessagingException
    {
        Receiver receiver;
        Source source = new Source();
        Target target = new Target();
        if (address == null || address.isEmpty() || address.equals("#"))
        {
            String temp = UUID.randomUUID().toString();
            receiver = _ssn.receiver(temp);
            source.setDynamic(true);
        }
        else
        {
            receiver = _ssn.receiver(address);
            source.setAddress(address);
        }
        receiver.setSource(source);
        receiver.setTarget(target);
        switch (mode)
        {
        case AT_MOST_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setSenderSettleMode(SenderSettleMode.SETTLED);
            break;
        case AT_LEAST_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            break;
        case EXACTLY_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.SECOND);
            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            break;
        }
        receiver.open();

        InboundLink inLink = new InboundLink(this, address, receiver, creditMode);
        inLink.setDynamicAddress(source.getDynamic());
        _links.put(receiver, inLink);
        receiver.setContext(inLink);
        _conn.write();
        return inLink;
    }

    @Override
    public void disposition(Message msg, MessageDisposition disposition, int... flags) throws MessageFormatException,
            MessagingException, NetworkException
    {
        switch (disposition)
        {
        case ACCEPTED:
            disposition(convertMessage(msg), ACCEPTED, flags);
            break;
        case REJECTED:
            disposition(convertMessage(msg), REJECTED, flags);
            break;
        case RELEASED:
            disposition(convertMessage(msg), RELEASED, flags);
            break;
        }
    }

    @Override
    public void settle(Message msg, int... flags) throws MessageFormatException, MessagingException, NetworkException
    {
        settle(convertMessage(msg), flags.length == 0 ? false : (flags[0] & CUMULATIVE) != 0, true);
    }

    @Override
    public void close() throws NetworkException
    {
        if (!_closed.get())
        {
            closeImpl();
            _conn.removeSession(_ssn);
            _conn.write();
        }
    }

    void closeImpl()
    {
        _closed.set(true);
        _ssn.close();
        for (Link link : _links.keySet())
        {
            _links.get(link).closeImpl();
        }
        _links.clear();
    }

    void removeLink(Link link)
    {
        _links.remove(link);
    }
    
    BaseConnection getConnection()
    {
        return _conn;
    }

    long getNextDeliveryTag()
    {
        return _deliveryTag.incrementAndGet();
    }

    long getNextIncommingSequence()
    {
        return _incommingSequence.incrementAndGet();
    }

    String getID()
    {
        return _id;
    }

    void checkClosed() throws MessagingException
    {
        if (_closed.get())
        {
            throw new MessagingException("Session is closed");
        }
    }

    void disposition(InboundMessage msg, DeliveryState state, int... flags) throws NetworkException
    {
        int flag = flags.length == 1 ? flags[0] : 0;
        boolean cumilative = (flag & CUMULATIVE) != 0;
        boolean settle = (flag & SETTLE) != 0;

        long count = cumilative ? _lastDispositionMark.get() : msg.getSequence();
        long end = msg.getSequence();

        while (count <= end)
        {
            Delivery d = _unsettled.get(count);
            if (d != null)
            {
                d.disposition(state);
            }
            count++;
        }
        _lastDispositionMark.set(end);
        if (settle)
        {
            settle(msg, cumilative, false);
        }
        _conn.write();
    }

    void settle(InboundMessage msg, boolean cumilative, boolean write) throws NetworkException
    {
        long count = cumilative ? _lastSettled.get() : msg.getSequence();
        long end = msg.getSequence();

        while (count <= end)
        {
            Delivery d = _unsettled.get(count);
            if (d != null)
            {
                if (!d.isSettled() && d.getLink().getReceiverSettleMode() == ReceiverSettleMode.FIRST)
                {
                    d.settle();
                    ((InboundLink) d.getLink().getContext()).decrementUnsettledCount();
                    _unsettled.remove(count);
                }
            }
            count++;
        }
        _lastSettled.set(end);
        _conn.write();
    }

    InboundMessage convertMessage(Message msg) throws MessageFormatException, MessagingException
    {
        if (!(msg instanceof InboundMessage))
        {
            throw new MessageFormatException("The supplied message is not a recognized type");
        }

        InboundMessage m = (InboundMessage) msg;

        if (m.getSessionID() != _id)
        {
            throw new MessagingException("The supplied message is not associated with this session");
        }

        return m;
    }
}