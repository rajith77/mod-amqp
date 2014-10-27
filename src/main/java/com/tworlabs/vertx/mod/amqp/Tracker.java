package com.tworlabs.vertx.mod.amqp;

import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.splash.messaging.DeliveryState;
import org.splash.messaging.MessageDisposition;
import org.splash.messaging.MessagingException;
import org.splash.messaging.TimeoutException;
import org.splash.messaging.Tracker;
import org.splash.util.ConditionManager;
import org.splash.util.ConditionManagerTimeoutException;

public class Tracker extends ContextAwareImpl implements Tracker
{
    private MessageDisposition _disposition = MessageDisposition.UNKNOWN;

    private DeliveryState _state = DeliveryState.UNKNOWN;

    private ConditionManager _pending = new ConditionManager(true);

    private boolean _settled = false;

    private Session _ssn;

    Tracker(Session ssn)
    {
        _ssn = ssn;
    }

    @Override
    public DeliveryState getState()
    {
        return _state;
    }

    @Override
    public MessageDisposition getDisposition()
    {
        return _disposition;
    }

    @Override
    public void awaitSettlement(int... flags) throws MessagingException
    {
        _pending.waitUntilFalse();
        if (_state == DeliveryState.LINK_FAILED)
        {
            throw new MessagingException(
                    "The link has failed due to the underlying network connection failure. The message associated with this delivery is in-doubt");
        }
    }

    @Override
    public void awaitSettlement(long timeout, TimeUnit unit, int... flags) throws MessagingException, TimeoutException
    {
        try
        {
            _pending.waitUntilFalse(unit.toMillis(timeout));
            if (_state == DeliveryState.LINK_FAILED)
            {
                throw new MessagingException(
                        "The link has failed due to the underlying network connection failure. The message associated with this delivery is in-doubt");
            }
        }
        catch (ConditionManagerTimeoutException e)
        {
            throw new TimeoutException("The delivery was not settled within the given time period", e);
        }
    }

    @Override
    public boolean isSettled()
    {
        return _settled;
    }

    void markSettled()
    {
        _settled = true;
        _pending.setValueAndNotify(false);
    }

    void setDisposition(org.apache.qpid.proton.amqp.transport.DeliveryState state)
    {
        if (state instanceof Accepted)
        {
            _disposition = MessageDisposition.ACCEPTED;
        }
        else if (state instanceof Released)
        {
            _disposition = MessageDisposition.RELEASED;
        }
        else if (state instanceof Rejected)
        {
            _disposition = MessageDisposition.REJECTED;
        }
    }

    void markLinkFailed()
    {
        _state = DeliveryState.LINK_FAILED;
        _pending.setValueAndNotify(false);
    }
}