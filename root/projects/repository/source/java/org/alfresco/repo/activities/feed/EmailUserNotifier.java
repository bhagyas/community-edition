package org.alfresco.repo.activities.feed;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.MailActionExecuter;
import org.alfresco.repo.security.authentication.AuthenticationContext;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.InitializingBean;

/**
 * Notifies the given user by sending activity feed information to their registered email address.
 * 
 * @since 4.0
 */
public class EmailUserNotifier extends AbstractUserNotifier implements InitializingBean
{
    private List<String> excludedEmailSuffixes;

    private AuthenticationContext authenticationContext;
    private ActionService actionService;

    public void setAuthenticationContext(AuthenticationContext authenticationContext)
	{
		this.authenticationContext = authenticationContext;
	}
	
	public void setActionService(ActionService actionService)
	{
		this.actionService = actionService;
	}

    public static Log getLogger()
    {
		return logger;
	}

	public static void setLogger(Log logger)
	{
		EmailUserNotifier.logger = logger;
	}

	public List<String> getExcludedEmailSuffixes()
	{
		return excludedEmailSuffixes;
	}

	public void setExcludedEmailSuffixes(List<String> excludedEmailSuffixes)
	{
		this.excludedEmailSuffixes = excludedEmailSuffixes;
	}

	/**
     * Perform basic checks to ensure that the necessary dependencies were injected.
     */
    protected void checkProperties()
    {
    	super.checkProperties();

        PropertyCheck.mandatory(this, "authenticationContext", authenticationContext);
        PropertyCheck.mandatory(this, "actionService", actionService);
    }
    
    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception
    {
    	checkProperties();
    }
    
	protected boolean skipUser(NodeRef personNodeRef)
	{
		Map<QName, Serializable> personProps = nodeService.getProperties(personNodeRef);
		String feedUserId = (String)personProps.get(ContentModel.PROP_USERNAME);
		String emailAddress = (String)personProps.get(ContentModel.PROP_EMAIL);
		Boolean emailFeedDisabled = (Boolean)personProps.get(ContentModel.PROP_EMAIL_FEED_DISABLED);

        if ((emailFeedDisabled != null) && (emailFeedDisabled == true))
        {
            return true;
        }
        
        if (authenticationContext.isSystemUserName(feedUserId) || authenticationContext.isGuestUserName(feedUserId))
        {
            // skip "guest" or "System" user
            return true;
        }
        
        if ((emailAddress == null) || (emailAddress.length() <= 0))
        {
            // skip user that does not have an email address
            if (logger.isDebugEnabled())
            {
                logger.debug("Skip for '"+feedUserId+"' since they have no email address set");
            }
            return true;
        }
        
        String lowerEmailAddress = emailAddress.toLowerCase();
        for (String excludedEmailSuffix : excludedEmailSuffixes)
        {
            if (lowerEmailAddress.endsWith(excludedEmailSuffix.toLowerCase()))
            {
                // skip user whose email matches exclude suffix
                if (logger.isDebugEnabled())
                {
                    logger.debug("Skip for '"+feedUserId+"' since email address is excluded ("+emailAddress+")");
                }
                return true;
            }
        }
        
        return false;
    }
	
	protected Long getFeedId(NodeRef personNodeRef)
	{
		Map<QName, Serializable> personProps = nodeService.getProperties(personNodeRef);

		// where did we get up to ?
		Long emailFeedDBID = (Long)personProps.get(ContentModel.PROP_EMAIL_FEED_ID);
		if (emailFeedDBID != null)
		{
			// increment min feed id
			emailFeedDBID++;
		}
		else
		{
			emailFeedDBID = -1L;
		}
		
		return emailFeedDBID;
	}
    
	protected void notifyUser(NodeRef personNodeRef, String subjectText, Map<String, Object> model, NodeRef templateNodeRef)
    {
        ParameterCheck.mandatory("personNodeRef", personNodeRef);

		Map<QName, Serializable> personProps = nodeService.getProperties(personNodeRef);
		String emailAddress = (String)personProps.get(ContentModel.PROP_EMAIL);
		
        Action mail = actionService.createAction(MailActionExecuter.NAME);
        
        mail.setParameterValue(MailActionExecuter.PARAM_TO, emailAddress);
        mail.setParameterValue(MailActionExecuter.PARAM_SUBJECT, subjectText);
        
        //mail.setParameterValue(MailActionExecuter.PARAM_TEXT, buildMailText(emailTemplateRef, model));
        mail.setParameterValue(MailActionExecuter.PARAM_TEMPLATE, templateNodeRef);
        mail.setParameterValue(MailActionExecuter.PARAM_TEMPLATE_MODEL, (Serializable)model);
        
        actionService.executeAction(mail, null);
    }

}
