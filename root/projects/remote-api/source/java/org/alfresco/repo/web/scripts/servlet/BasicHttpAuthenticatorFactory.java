/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
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
package org.alfresco.repo.web.scripts.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.repo.security.authentication.Authorization;
import org.alfresco.repo.web.auth.AuthenticationListener;
import org.alfresco.repo.web.auth.BasicAuthCredentials;
import org.alfresco.repo.web.auth.GuestCredentials;
import org.alfresco.repo.web.auth.TicketCredentials;
import org.alfresco.repo.web.auth.WebCredentials;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.surf.util.Base64;
import org.springframework.extensions.webscripts.Authenticator;
import org.springframework.extensions.webscripts.Description.RequiredAuthentication;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.servlet.ServletAuthenticatorFactory;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRequest;
import org.springframework.extensions.webscripts.servlet.WebScriptServletResponse;

/**
 * HTTP Basic Authentication
 * 
 * @author davidc
 */
public class BasicHttpAuthenticatorFactory implements ServletAuthenticatorFactory
{
    // Logger
    private static Log logger = LogFactory.getLog(BasicHttpAuthenticator.class);

    // Component dependencies
    private AuthenticationService authenticationService;
    private AuthenticationListener listener;

    
    /**
     * @param authenticationService
     */
    public void setAuthenticationService(AuthenticationService authenticationService)
    {
        this.authenticationService = authenticationService;
    }
    
    /**
     * Set the listener for authentication events, generated by {@link BasicHttpAuthenticator} instances.
     */
    public void setAuthenticationListener(AuthenticationListener listener)
    {
    	this.listener = listener;
    }

    /* (non-Javadoc)
     * @see org.alfresco.web.scripts.servlet.ServletAuthenticatorFactory#create(org.alfresco.web.scripts.servlet.WebScriptServletRequest, org.alfresco.web.scripts.servlet.WebScriptServletResponse)
     */
    public Authenticator create(WebScriptServletRequest req, WebScriptServletResponse res)
    {
        return new BasicHttpAuthenticator(req, res, listener);
    }
    
    /**
     * Generate a {@link WebCredentials} instance from information contained in auth.
     */
    private WebCredentials getCredentials(Authorization auth)
    {
        if (auth.isTicket()) 
        {
            return new TicketCredentials(auth.getTicket());
        }
        else
        {
            return new BasicAuthCredentials(auth.getUserName(), auth.getPassword());
        }

    }
    
    
    /**
     * HTTP Basic Authentication
     * 
     * @author davidc
     */
    public class BasicHttpAuthenticator implements Authenticator
    {
        // dependencies
        private WebScriptServletRequest servletReq;
        private WebScriptServletResponse servletRes;
        
        private String authorization;
        private String ticket;
        private AuthenticationListener listener;
        
        /**
         * Construct
         * 
         * @param authenticationService
         * @param req
         * @param res
         * @param listener
         */
        public BasicHttpAuthenticator(WebScriptServletRequest req, WebScriptServletResponse res, AuthenticationListener listener)
        {
            this.servletReq = req;
            this.servletRes = res;
            
            HttpServletRequest httpReq = servletReq.getHttpServletRequest();
            
            this.listener = listener;
            
            this.authorization = httpReq.getHeader("Authorization");
            this.ticket = httpReq.getParameter("alf_ticket");
        }
    
        /* (non-Javadoc)
         * @see org.alfresco.web.scripts.Authenticator#authenticate(org.alfresco.web.scripts.Description.RequiredAuthentication, boolean)
         */
        public boolean authenticate(RequiredAuthentication required, boolean isGuest)
        {
            boolean authorized = false;
    
            //
            // validate credentials
            // 
            
            HttpServletResponse res = servletRes.getHttpServletResponse();
            
            if (logger.isDebugEnabled())
            {
                logger.debug("HTTP Authorization provided: " + (authorization != null && authorization.length() > 0));
                logger.debug("URL ticket provided: " + (ticket != null && ticket.length() > 0));
            }
            
            // If they requested explicit guest authentication,
            //  Authenticate as guest (if allowed)
            if (isGuest && RequiredAuthentication.guest == required)
            {
                if (logger.isDebugEnabled())
                    logger.debug("Authenticating as Guest");
    
                try 
                {
                    authenticationService.authenticateAsGuest();
                    listener.userAuthenticated(new GuestCredentials());
                    authorized = true;
                }
                catch (AuthenticationException ex)
                {
                 // failed authentication
                    listener.authenticationFailed(new GuestCredentials());
                }
            }
            
            // authenticate as specified by explicit ticket on url
            else if (ticket != null && ticket.length() > 0)
            {
                try
                {
                    if (logger.isDebugEnabled())
                        logger.debug("Authenticating (URL argument) ticket " + ticket);
        
                    // assume a ticket has been passed
                    authenticationService.validate(ticket);
                    listener.userAuthenticated(new TicketCredentials(ticket));
                    authorized = true;
                }
                catch(AuthenticationException e)
                {
                    // failed authentication
                    listener.authenticationFailed(new TicketCredentials(ticket));
                }
            }
            
            // authenticate as specified by HTTP Basic Authentication
            else if (authorization != null && authorization.length() > 0)
            {
                String[] authorizationParts = authorization.split(" ");
                if (!authorizationParts[0].equalsIgnoreCase("basic"))
                {
                    throw new WebScriptException("Authorization '" + authorizationParts[0] + "' not supported.");
                }
                
                String decodedAuthorisation = new String(Base64.decode(authorizationParts[1]));
                Authorization auth = new Authorization(decodedAuthorisation);
                try
                {
                    if (auth.isTicket())
                    {
                        if (logger.isDebugEnabled())
                            logger.debug("Authenticating (BASIC HTTP) ticket " + auth.getTicket());
    
                        // assume a ticket has been passed
                        authenticationService.validate(auth.getTicket());
                        listener.userAuthenticated(getCredentials(auth));
                        authorized = true;
                    }
                    else
                    {
                        if (logger.isDebugEnabled())
                            logger.debug("Authenticating (BASIC HTTP) user " + auth.getUserName());
    
                        // No longer need a special call to authenticate as guest
                        // Leave guest name resolution up to the services
                        authenticationService.authenticate(auth.getUserName(), auth.getPassword().toCharArray());
                        listener.userAuthenticated(getCredentials(auth));
                        authorized = true;
                    }
                }
                catch(AuthenticationException e)
                {
                    // failed authentication
                    listener.authenticationFailed(getCredentials(auth));
                }
            }
    
            //
            // request credentials if not authorized
            //
            
            if (!authorized)
            {
                if (logger.isDebugEnabled())
                    logger.debug("Requesting authorization credentials");
                
                res.setStatus(401);
                res.setHeader("WWW-Authenticate", "Basic realm=\"Alfresco\"");
            }
            return authorized;
        }
        
        /* (non-Javadoc)
         * @see org.alfresco.web.scripts.Authenticator#emptyCredentials()
         */
        public boolean emptyCredentials()
        {
            return ((ticket == null || ticket.length() == 0) && (authorization == null || authorization.length() == 0));
        }
    }

}