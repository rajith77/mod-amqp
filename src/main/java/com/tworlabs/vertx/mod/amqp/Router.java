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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.message.Message;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.logging.Logger;

import com.tworlabs.vertx.mod.amqp.RouterConfig.RouteEntry;

public class Router implements EventHandler, Handler<org.vertx.java.core.eventbus.Message<JsonObject>>
{
    private final Vertx _vertx;

    private final EventBus _eb;

    private final MessageFactory _msgFactory;

    private final OutboundLink _defaultOutboundLink;

    private final List<Connection> _outboundConnections = new CopyOnWriteArrayList<Connection>();

    private final List<InboundLink> _inboundLinks = new CopyOnWriteArrayList<InboundLink>();

    private final Map<String, OutboundLink> _outboundLinks = new ConcurrentHashMap<String, OutboundLink>();

    private final RouterConfig _config;

    private final NetClient _client;

    private final NetServer _server;

    private final Logger _logger;

    Router(Vertx vertx, MessageFactory msgFactory, RouterConfig config, Logger logger) throws MessagingException
    {
        _vertx = vertx;
        _eb = _vertx.eventBus();
        _logger = logger;
        _client = _vertx.createNetClient();
        _server = _vertx.createNetServer();
        _msgFactory = msgFactory;
        _config = config;

        _logger.info("Registering handlers");
        _eb.registerHandler(config.getDefaultHandlerAddress(), this);
        _eb.registerHandler("address.a", this);
        _eb.registerHandler("vertx.mod-amqp", this);
        _logger.info("Registering default handler : " + config.getDefaultHandlerAddress());
        for (String handlerAddress : config.getHandlerAddressList())
        {
            _logger.info("Registering handler : " + handlerAddress);
            _eb.registerHandler(handlerAddress, this);
        }

        _defaultOutboundLink = findOutboundLink(config.getDefaultOutboundAddress());

        final AtomicBoolean serverOK = new AtomicBoolean(true);
        final CountDownLatch latch = new CountDownLatch(1);
        _server.connectHandler(new InboundConnectionHandler(this));
        _server.listen(config.getInboundPort(), config.getInboundHost(), new AsyncResultHandler<NetServer>()
        {
            public void handle(AsyncResult<NetServer> result)
            {
                if (result.failed())
                {
                    serverOK.set(false);
                }
                latch.countDown();
            }
        });
        try
        {
            latch.await();
        }
        catch (InterruptedException e)
        {
        }
        if (!serverOK.get())
        {
            throw new MessagingException(String.format("Server was unable to bind to %s:%s", config.getInboundHost(),
                    config.getInboundPort()));
        }
    }

    public void stop()
    {
        for (Connection con : _outboundConnections)
        {
            con.close();
        }

        _server.close();
    }

    Connection findConnection(final ConnectionSettings settings) throws MessagingException
    {
        for (Connection con : _outboundConnections)
        {
            if (con.getSettings().getHost().equals(settings.getHost())
                    && con.getSettings().getPort() == settings.getPort())
            {
                return con;
            }
        }

        ConnectionResultHander handler = new ConnectionResultHander(settings, this);
        _client.connect(settings.getPort(), settings.getHost(), handler);
        _logger.info(String.format("Connecting to AMQP peer at %s:%s", settings.getHost(), settings.getPort()));
        return handler.getConnection();
    }

    OutboundLink findOutboundLink(String url) throws MessagingException
    {
        if (_outboundLinks.containsKey(url))
        {
            return _outboundLinks.get(url);
        }
        else
        {
            final ConnectionSettings settings = URLParser.parse(url);
            Connection con = findConnection(settings);
            OutboundLink link = con.createOutBoundLink(settings.getTarget());
            _outboundLinks.put(url, link);
            return link;
        }
    }

    void send(String address, JsonObject ebMsg) throws MessagingException
    {
        Message msg = _msgFactory.convert(ebMsg);
        List<OutboundLink> links = routeOutbound(address);
        if (links.size() == 0)
        {
            links.add(_defaultOutboundLink);
        }

        for (OutboundLink link : links)
        {
            link.send(_msgFactory.convert(ebMsg));
        }
    }

    List<OutboundLink> routeOutbound(String address) throws MessagingException
    {
        List<OutboundLink> links = new ArrayList<OutboundLink>();
        for (String key : _config.getOutboundRoutes().keySet())
        {
            RouteEntry route = _config.getOutboundRoutes().get(key);
            if (route.getPattern().matcher(address).matches())
            {
                for (String addr : route.getAddressList())
                {
                    links.add(findOutboundLink(addr));
                }
            }
        }
        return links;
    }

    @Override
    public void handle(org.vertx.java.core.eventbus.Message<JsonObject> m)
    {
        try
        {
            System.out.println("Received msg " + m);
            _logger.info("Received msg " + m);
            send(m.address(), m.body());
        }
        catch (MessagingException e)
        {
            System.out.println("Exception routing message " + e);
            e.printStackTrace();
        }
    }

    // ------------ Event Handler ------------------------

    public void onConnectionOpen(Connection con)
    {
    }

    public void onConnectionClosed(Connection conn)
    {
    }

    public void onSessionOpen(Session ssn)
    {
    }

    public void onSessionClosed(Session ssn)
    {

    }

    public void onOutboundLinkOpen(OutboundLink link)
    {
        Connection con = link.getConnection();
        if (con.isInbound())
        {
            String address = link.getAddress();
            String url = new StringBuilder(con.getSettings().getHost()).append(":").append(con.getSettings().getPort())
                    .append("/").append(address).toString();
            _outboundLinks.put(url, link);
            if (_config.getOutboundRoutes().containsKey(address))
            {
                _config.getOutboundRoutes().get(address).add(url);
            }
            else
            {
                _config.getOutboundRoutes().put(address, RouterConfig.createRouteEntry(_config, address, url));
            }
        }
    }

    public void onOutboundLinkClosed(OutboundLink link)
    {
        Connection con = link.getConnection();
        String address = link.getAddress();
        String url = new StringBuilder(con.getSettings().getHost()).append(":").append(con.getSettings().getPort())
                .append("/").append(address).toString();
        _outboundLinks.remove(url);
        if (link.getConnection().isInbound() && _config.getOutboundRoutes().containsKey(address))
        {
            RouteEntry entry = _config.getOutboundRoutes().get(address);
            entry.remove(url);
            if (entry.getAddressList().size() == 0)
            {
                _config.getOutboundRoutes().remove(address);
            }
        }
    }

    public void onOutboundLinkCredit(OutboundLink link, int credits)
    {
    }

    public void onClearToSend(OutboundLink link)
    {
    }

    public void onSettled(Tracker tracker)
    {
    }

    public void onInboundLinkOpen(InboundLink link)
    {
    }

    public void onInboundLinkClosed(InboundLink link)
    {
    }

    public void onCreditOffered(InboundLink link, int offered)
    {
    }

    public void onMessage(InboundLink link, AmqpMessage msg)
    {
        JsonObject out = _msgFactory.convert(msg.getProtocolMessage());
        String key = null;
        switch (_config.getInboundRoutingPropertyType())
        {
        case LINK_NAME:
            key = link.getAddress();
            break;
        case SUBJECT:
            key = msg.getSubject();
            break;
        case MESSAGE_ID:
            key = msg.getMessageId().toString();
            break;
        case CORRELATION_ID:
            key = msg.getCorrelationId().toString();
            break;
        case ADDRESS:
            key = msg.getAddress();
        case REPLY_TO:
            key = msg.getReplyTo();
        case CUSTOM:
            key = (String) msg.getApplicationProperties().get(_config.getInboundRoutingPropertyName());
            break;
        }

        List<String> addressList = new ArrayList<String>();
        if (_config.getInboundRoutes().size() == 0)
        {
            addressList.add(key);
        }
        else
        {
            for (String k : _config.getInboundRoutes().keySet())
            {
                RouteEntry route = _config.getInboundRoutes().get(k);
                if (route.getPattern().matcher(key).matches())
                {
                    for (String addr : route.getAddressList())
                    {
                        addressList.add(addr);
                    }
                }
            }
        }

        if (addressList.size() == 0 && _config.getDefaultInboundAddress() != null)
        {
            addressList.add(_config.getDefaultInboundAddress());
        }

        for (String address : addressList)
        {
            _eb.send(address, out);
        }
    }

    // ---------- / Event Handler -----------------------

    // ---------- Helper classes
    class ConnectionResultHander implements Handler<AsyncResult<NetSocket>>
    {
        final CountDownLatch _resultReceived = new CountDownLatch(1);

        final ConnectionSettings _settings;

        EventHandler _handler;

        Connection _connection;

        Throwable _cause;

        ConnectionResultHander(ConnectionSettings settings, EventHandler handler)
        {
            _settings = settings;
            _handler = handler;
        }

        public void handle(AsyncResult<NetSocket> result)
        {
            if (result.succeeded())
            {
                _connection = new Connection(_settings, result.result(), _handler, false);
                _connection.open();
                _resultReceived.countDown();
            }
            else
            {
                _cause = result.cause();
                _connection = null;
                _resultReceived.countDown();
            }
        }

        Connection getConnection() throws MessagingException
        {
            try
            {
                _resultReceived.await();
            }
            catch (InterruptedException e)
            {
                // ignore
            }
            if (_connection == null)
            {
                throw new MessagingException(String.format("Error creating connection for %s:%s", _settings.host,
                        _settings.port), _cause);
            }
            return _connection;
        }
    }

    class InboundConnectionHandler implements Handler<NetSocket>
    {
        EventHandler _handler;

        InboundConnectionHandler(EventHandler handler)
        {
            _handler = handler;
        }

        public void handle(NetSocket sock)
        {
            ConnectionSettings settings = new ConnectionSettings();
            settings.setHost(sock.remoteAddress().getHostName());
            settings.setPort(sock.remoteAddress().getPort());
            Connection connection = new Connection(settings, sock, _handler, true);
            connection.open();
        }
    }
}