/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.alfresco.email.server.handler;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ForumModel;
import org.alfresco.service.cmr.email.EmailMessage;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

/**
 * Handler implementation address to forum node.
 * 
 * @author maxim
 * @since 2.2
 */
public class ForumEmailMessageHandler extends AbstractForumEmailMessageHandler
{
    /**
     * {@inheritDoc}
     */
    public void processMessage(NodeRef nodeRef, EmailMessage message)
    {
        String messageSubject;

        if (message.getSubject() != null)
        {
            messageSubject = message.getSubject();
        }
        else
        {
            messageSubject = "EMPTY_SUBJECT_" + System.currentTimeMillis();
        }

        QName nodeTypeQName = getNodeService().getType(nodeRef);

        if (getDictionaryService().isSubClass(nodeTypeQName, ForumModel.TYPE_FORUM))
        {
            NodeRef topicNode = getTopicNode(nodeRef, messageSubject);

            if (topicNode == null)
            {
                topicNode = addTopicNode(nodeRef, messageSubject);
            }
            addPostNode(topicNode, message);
        }
        else
        {
            throw new AlfrescoRuntimeException("\n" +
                    "Message handler " + this.getClass().getName() + " cannot handle type " + nodeTypeQName + ".\n" +
                    "Check the message handler mappings.");
        }
    }
}
