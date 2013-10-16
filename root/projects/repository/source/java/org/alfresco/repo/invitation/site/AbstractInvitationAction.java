/*
 * Copyright (C) 2005-2011 Alfresco Software Limited.
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

package org.alfresco.repo.invitation.site;

import org.alfresco.repo.invitation.InviteHelper;
import org.alfresco.repo.workflow.jbpm.JBPMSpringActionHandler;
import org.springframework.beans.factory.BeanFactory;

/**
 * @author Nick Smith
 * @since 4.0
 *
 */
public abstract class AbstractInvitationAction extends JBPMSpringActionHandler
{
    private static final long serialVersionUID = -6497378327090711383L;
    protected InviteHelper inviteHelper;

    /**
    * {@inheritDoc}
     */
    @Override
    protected void initialiseHandler(BeanFactory factory)
    {
        this.inviteHelper= (InviteHelper)factory.getBean(InviteHelper.NAME);
    }
}
