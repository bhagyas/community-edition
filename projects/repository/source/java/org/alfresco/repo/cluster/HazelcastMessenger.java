/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.repo.cluster;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hazelcast.core.ITopic;
import com.hazelcast.core.MessageListener;

/**
 * Hazelcast-based implementation of the {@link Messenger} interface.
 * 
 * @see HazelcastMessengerFactory
 * @author Matt Ward
 */
public class HazelcastMessenger<T extends Serializable> implements Messenger<T>, MessageListener<T>
{
    private ITopic<T> topic;
    private MessageReceiver<T> receiverDelegate;
    private String address;
    private final static Log logger = LogFactory.getLog(HazelcastMessenger.class);
    
    /**
     * @param topic
     */
    public HazelcastMessenger(ITopic<T> topic, String address)
    {
        this.topic = topic;
        this.address = address;
    }


    @Override
    public void send(T message)
    {
        if (logger.isTraceEnabled())
        {
            String digest = StringUtils.abbreviate(message.toString(), 50);
            logger.trace("Sending [source: " + address + "]: " + digest);
        }
        topic.publish(message);
    }

    @Override
    public void setReceiver(MessageReceiver<T> receiver)
    {
        // Install a delegate to ready to handle incoming messages.
        receiverDelegate = receiver;
        // Start receiving messages.
        topic.addMessageListener(this);
    }

    @Override
    public void onMessage(T message)
    {
        if (logger.isTraceEnabled())
        {
            String digest = StringUtils.abbreviate(message.toString(), 50);
            logger.trace("Received [destination: " + address + "] (delegating to receiver): " + digest);
        }
        receiverDelegate.onReceive(message);
    }

    @Override
    public boolean isConnected()
    {
        return true;
    }
    
    protected ITopic<T> getTopic()
    {
        return topic;
    }


    @Override
    public String getAddress()
    {
        return address;
    }


    @Override
    public String toString()
    {
        return "HazelcastMessenger[connected=" + isConnected() +
                    ", topic=" + getTopic() +
                    ", address=" + getAddress() + "]";
    }
}
