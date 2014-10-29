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

import java.util.List;
import java.util.Map;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonElement;
import org.vertx.java.core.json.JsonObject;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

class MessageFactory
{
    private byte[] outbuffer = new byte[1024 * 10];

    private void convert(JsonObject in, Properties out)
    {
        if (in.containsField("to"))
        {
            out.setTo(in.getString("to"));
        }
        if (in.containsField("subject"))
        {
            out.setSubject(in.getString("subject"));
        }
        if (in.containsField("reply_to"))
        {
            out.setReplyTo(in.getString("reply_to"));
        }
        if (in.containsField("message_id"))
        {
            // TODO: handle other types (UUID and long)
            out.setMessageId(in.getString("message_id"));
        }
        if (in.containsField("correlation_id"))
        {
            // TODO: handle other types (UUID and long)
            out.setCorrelationId(in.getString("correlation_id"));
        }
        // TODO: handle other fields
    }

    private void convert(Properties in, JsonObject out)
    {
        if (in.getTo() != null)
        {
            out.putString("to", in.getTo());
        }
        if (in.getSubject() != null)
        {
            out.putString("subject", in.getSubject());
        }
        if (in.getReplyTo() != null)
        {
            out.putString("reply_to", in.getReplyTo());
        }
        if (in.getMessageId() != null)
        {
            out.putString("message_id", in.getMessageId().toString());
        }
        if (in.getCorrelationId() != null)
        {
            out.putString("correlation_id", in.getCorrelationId().toString());
        }
        // TODO: handle other fields
    }

    Message convert(JsonObject in) throws MessageFormatException
    {
        Message out = Message.Factory.create();

        if (in.containsField("properties"))
        {
            out.setProperties(new Properties());
            convert(in.getObject("properties"), out.getProperties());
        }

        if (in.containsField("application_properties"))
        {
            out.setApplicationProperties(new ApplicationProperties(in.getObject("application_properties").toMap()));
        }

        if (in.containsField("body"))
        {
            String bodyType = in.getString("body_type");
            if (bodyType == null || bodyType.equals("value"))
            {
                Object o = in.getField("body");
                if (o instanceof JsonObject)
                {
                    o = ((JsonObject) o).toMap();
                }
                else if (o instanceof JsonArray)
                {
                    o = ((JsonArray) o).toList();
                }
                out.setBody(new AmqpValue(o));
            }
            else if (bodyType.equals("data"))
            {
                out.setBody(new Data(new Binary(in.getBinary("body"))));
            }
            else if (bodyType.equals("sequence"))
            {
                out.setBody(new AmqpSequence((List) in.getField("body")));
            }
            else
            {
                throw new MessageFormatException("Unrecognised body type: " + bodyType);
            }
        }
        System.out.println("Converted " + in + " to AMQP 1.0 message: " + out);
        return out;
    }

    private static Object toJsonable(Object in)
    {
        if (in instanceof Number || in instanceof String)
        {
            return in;
        }
        else if (in instanceof Map)
        {
            JsonObject out = new JsonObject();
            for (Object o : ((Map) in).entrySet())
            {
                Map.Entry e = (Map.Entry) o;
                out.putValue((String) e.getKey(), toJsonable(e.getValue()));
            }
            return out;
        }
        else if (in instanceof List)
        {
            JsonArray out = new JsonArray();
            for (Object i : (List) in)
            {
                out.add(toJsonable(i));
            }
            return out;
        }
        else
        {
            System.out.println("Warning: can't convert object of type " + in.getClass() + " to JSON");
            return in.toString();
        }
    }

    JsonObject convert(Message in)
    {
        JsonObject out = new JsonObject();
        Properties p = in.getProperties();
        if (p != null)
        {
            JsonObject props = new JsonObject();
            convert(p, props);
            out.putObject("properties", props);
        }
        ApplicationProperties ap = in.getApplicationProperties();
        if (ap != null && ap.getValue() != null)
        {
            out.putObject("application_properties", new JsonObject(ap.getValue()));
        }
        Section body = in.getBody();
        if (body instanceof AmqpValue)
        {
            out.putValue("body", toJsonable(((AmqpValue) body).getValue()));
            out.putString("body_type", "value");
        }
        else if (body instanceof Data)
        {
            out.putBinary("body", ((Data) body).getValue().getArray());
            out.putString("body_type", "data");
        }
        else if (body instanceof AmqpSequence)
        {
            out.putArray("body", new JsonArray(((AmqpSequence) body).getValue()));
            out.putString("body_type", "sequence");
        }
        return out;
    }

    /*void send(org.apache.qpid.proton.engine.Sender sender, JsonObject message)
    {
        Message m = convert(message);
        int written = m.encode(outbuffer, 0, outbuffer.length);
        sender.send(outbuffer, 0, written);
        sender.advance();
    }

    JsonObject decode(byte[] buffer, int offset, int length)
    {
        Message m = factory.createMessage();
        int read = m.decode(buffer, offset, length);
        return convert(m);
    }*/
}
