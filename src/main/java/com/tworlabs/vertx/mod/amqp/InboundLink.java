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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.splash.messaging.Action;
import org.splash.messaging.CreditMode;
import org.splash.messaging.InboundLink;
import org.splash.messaging.MessagingException;
import org.splash.messaging.NetworkException;
import org.splash.messaging.ReasonCode;

class InboundLink extends BaseLink implements Action, InboundLink
{
    private static int DEFAULT_CREDITS = Integer.getInteger("splash.default.credits", 1);

    private CreditMode _creditMode;

    private int _credits = 0;

    private AtomicInteger _unsettled = new AtomicInteger(0);

    InboundLink(Session ssn, String address, Link link, CreditMode creditMode)
    {
        super(ssn, address, link);
        _creditMode = creditMode;
    }

    @Override
    void init() throws NetworkException
    {
        _link.open();
        if (_creditMode == CreditMode.AUTO && DEFAULT_CREDITS > 0)
        {
            _credits = DEFAULT_CREDITS;
            ((Receiver) _link).flow(_credits);
        }
        _ssn.getConnection().write();
    }

    void issueCredits(int credits, boolean drain) throws NetworkException
    {
        Receiver receiver = (Receiver) _link;
        if (drain)
        {
            //receiver.setDrain(true);
        }
        receiver.flow(credits);
        _ssn.getConnection().write();
    }

    void decrementUnsettledCount()
    {
        _unsettled.decrementAndGet();
    }

    void issuePostReceiveCredit() throws NetworkException
    {
        if (_creditMode == CreditMode.AUTO)
        {
            if (_credits == 1)
            {
                issueCredits(1, false);
            }
            else if (_unsettled.get() < _credits / 2)
            {
                issueCredits(_credits - _unsettled.get(), false);
            }
        }
    }

    @Override
    public CreditMode getCreditMode()
    {
        return _creditMode;
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        checkClosed();
        return _unsettled.get();
    }

    @Override
    public void setCredits(int credits) throws MessagingException, NetworkException
    {
        checkClosed();
        if (credits < 0)
        {
            throw new MessagingException("Capacity cannot be negative");
        }
        _credits = credits;
        issueCredits(_credits, _creditMode == CreditMode.AUTO);
    }

    @Override
    public void accept() throws NetworkException
    {
        init();
    }

    @Override
    public void reject(ReasonCode code, String desc, String alternateAddress) throws NetworkException
    {
        // TODO Auto-generated method stub
    }

}