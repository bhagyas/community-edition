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
package org.alfresco.repo.web.scripts.calendar;

import java.util.Iterator;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.util.PropertyMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.TestWebScriptServer.DeleteRequest;
import org.springframework.extensions.webscripts.TestWebScriptServer.GetRequest;
import org.springframework.extensions.webscripts.TestWebScriptServer.PostRequest;
import org.springframework.extensions.webscripts.TestWebScriptServer.PutRequest;
import org.springframework.extensions.webscripts.TestWebScriptServer.Response;

/**
 * Unit Test to test the Calendaring Web Script API
 * 
 * @author Nick Burch
 * @since 4.0
 */
public class CalendarRestApiTest extends BaseWebScriptTest
{
    @SuppressWarnings("unused")
    private static Log logger = LogFactory.getLog(CalendarRestApiTest.class);

    private MutableAuthenticationService authenticationService;
    private AuthenticationComponent authenticationComponent;
    private PersonService personService;
    private NodeService nodeService;
    private SiteService siteService;
    
    private static final String USER_ONE = "UserOneSecondToo";
    private static final String USER_TWO = "UserTwoSecondToo";
    private static final String SITE_SHORT_NAME_CALENDAR = "CalendarSiteShortNameTest";
    
    private static final String EVENT_TITLE_ONE = "TestEventOne";
    private static final String EVENT_TITLE_TWO = "TestEventTwo";
    private static final String EVENT_TITLE_THREE = "TestEventThree";

    private static final String URL_EVENT_BASE = "/calendar/event/" + SITE_SHORT_NAME_CALENDAR + "/"; 
    private static final String URL_EVENTS_LIST = "/calendar/eventList"; 
    private static final String URL_EVENTS_LIST_ICS = "/calendar/eventList-" + SITE_SHORT_NAME_CALENDAR + ".ics?format=calendar"; 
    private static final String URL_EVENT_CREATE = "/calendar/create"; 
    
    private static final String URL_USER_EVENTS_LIST = "/calendar/events/user"; 
    private static final String URL_USER_SITE_EVENTS_LIST = "/calendar/events/" + SITE_SHORT_NAME_CALENDAR + "/user"; 

    
    // General methods

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        
        this.authenticationService = (MutableAuthenticationService)getServer().getApplicationContext().getBean("AuthenticationService");
        this.authenticationComponent = (AuthenticationComponent)getServer().getApplicationContext().getBean("authenticationComponent");
        this.personService = (PersonService)getServer().getApplicationContext().getBean("PersonService");
        this.nodeService = (NodeService)getServer().getApplicationContext().getBean("NodeService");
        this.siteService = (SiteService)getServer().getApplicationContext().getBean("SiteService");
        
        // Authenticate as user
        this.authenticationComponent.setCurrentUser(AuthenticationUtil.getAdminUserName());
        
        // Create test site
        // - only create the site if it doesn't already exist
        SiteInfo siteInfo = this.siteService.getSite(SITE_SHORT_NAME_CALENDAR);
        if (siteInfo == null)
        {
            this.siteService.createSite("CalendarSitePreset", SITE_SHORT_NAME_CALENDAR, "CalendarSiteTitle", "BlogSiteDescription", SiteVisibility.PUBLIC);
        }
        
        // Ensure the calendar container is there
        if (!siteService.hasContainer(SITE_SHORT_NAME_CALENDAR, "calendar"))
        {
            siteService.createContainer(SITE_SHORT_NAME_CALENDAR, "calendar", null, null);
        }
        
        // Create users
        createUser(USER_ONE, SiteModel.SITE_COLLABORATOR);
        createUser(USER_TWO, SiteModel.SITE_COLLABORATOR);

        // Do tests as inviter user
        this.authenticationComponent.setCurrentUser(USER_ONE);
    }
    
    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        
        // admin user required to delete user
        this.authenticationComponent.setCurrentUser(AuthenticationUtil.getAdminUserName());
        
        // delete invite site
        siteService.deleteSite(SITE_SHORT_NAME_CALENDAR);
        
        // delete the users
        personService.deletePerson(USER_ONE);
        if (this.authenticationService.authenticationExists(USER_ONE))
        {
           this.authenticationService.deleteAuthentication(USER_ONE);
        }
        
        personService.deletePerson(USER_TWO);
        if (this.authenticationService.authenticationExists(USER_TWO))
        {
           this.authenticationService.deleteAuthentication(USER_TWO);
        }
    }
    
    private void createUser(String userName, String role)
    {
        // if user with given user name doesn't already exist then create user
        if (this.authenticationService.authenticationExists(userName) == false)
        {
            // create user
            this.authenticationService.createAuthentication(userName, "password".toCharArray());
            
            // create person properties
            PropertyMap personProps = new PropertyMap();
            personProps.put(ContentModel.PROP_USERNAME, userName);
            personProps.put(ContentModel.PROP_FIRSTNAME, "FirstName123");
            personProps.put(ContentModel.PROP_LASTNAME, "LastName123");
            personProps.put(ContentModel.PROP_EMAIL, "FirstName123.LastName123@email.com");
            personProps.put(ContentModel.PROP_JOBTITLE, "JobTitle123");
            personProps.put(ContentModel.PROP_JOBTITLE, "Organisation123");
            
            // create person node for user
            this.personService.createPerson(personProps);
        }
        
        // add the user as a member with the given role
        this.siteService.setMembership(SITE_SHORT_NAME_CALENDAR, userName, role);
    }
    
    
    // Test helper methods
    
    private JSONObject validateAndParseJSON(String json) throws Exception
    {
       // TODO Do full validation, rather than just the basic bits
       //  that the JSONObject constructor provides
       return new JSONObject(json);
    }
    
    private JSONObject getEntries(String username, String from) throws Exception
    {
       String url = URL_EVENTS_LIST + "?site=" + SITE_SHORT_NAME_CALENDAR;
       if (username != null)
       {
          url = URL_USER_SITE_EVENTS_LIST;
       }
       if (from != null)
       {
          if (url.indexOf('/') > 0)
          {
             url += "&";
          }
          else
          {
             url += "?";
          }
          url += "from=" + from;
       }
       
       Response response = sendRequest(new GetRequest(url), 200);
       JSONObject result = validateAndParseJSON(response.getContentAsString());
       return result;
    }
    
    private JSONObject getEntry(String name, int expectedStatus) throws Exception
    {
       Response response = sendRequest(new GetRequest(URL_EVENT_BASE + name), expectedStatus);
       if (expectedStatus == Status.STATUS_OK)
       {
          JSONObject result = validateAndParseJSON(response.getContentAsString());
          return result;
       }
       else
       {
          return null;
       }
    }
    
    /**
     * Creates a 1 hour, non-all day event on the 29th of June
     */
    private JSONObject createEntry(String name, String where, String description, 
          int expectedStatus) throws Exception
    {
       String date = "2011-06-29"; // A wednesday
       String start = "12:00";
       String end = "13:00";
       
       // Old style is:
       //   "from": "2011/06/29",
       //   "to":   "2011/06/29",
       //   "start": "12:00",
       //   "end":   "13:00",
       
       JSONObject json = new JSONObject();
       json.put("startAt", date + "T" + start + ":00+01:00");
       json.put("endAt",   date + "T" + end + ":00+01:00");
       
       return createEntry(name, where, description, json, expectedStatus);
    }
    
    /**
     * Creates an event, with the date properties manually set
     */
    private JSONObject createEntry(String name, String where, String description, 
          JSONObject datesJSON, int expectedStatus) throws Exception
    {
       JSONObject json = new JSONObject();
       
       json.put("site", SITE_SHORT_NAME_CALENDAR);
       json.put("what", name);
       json.put("where", where);
       json.put("desc", description);
       json.put("tags", "");
       json.put("docfolder", "");
       json.put("page", "calendar");
       
       // Copy in the date properties
       Iterator<String> datesIT = datesJSON.keys();
       while(datesIT.hasNext())
       {
          String key = datesIT.next();
          json.put(key, datesJSON.get(key));
       }
       
       Response response = sendRequest(new PostRequest(URL_EVENT_CREATE, json.toString(), "application/json"), expectedStatus);
       if (expectedStatus == Status.STATUS_OK)
       {
          JSONObject result = validateAndParseJSON(response.getContentAsString());
          if (result.has("event"))
          {
             return result.getJSONObject("event");
          }
          return result;
       }
       else
       {
          return null;
       }
    }
    
    /**
     * Updates the event to be a 2 hour, non-all day event on the 28th of June
     */
    private JSONObject updateEntry(String name, String what, String where, String description, 
          boolean withRecurrence, int expectedStatus) throws Exception
    {
       String date = "2011/06/28"; // A Tuesday
       String start = "11:30";
       String end = "13:30";
       
       JSONObject json = new JSONObject();
       json.put("what", what);
       json.put("where", where);
       json.put("desc", description);
       json.put("from", date);
       json.put("to", date);
//       json.put("fromdate", "Tuesday, 30 June 2011"); // Not needed
//       json.put("todate", "Tuesday, 30 June 2011"); // Not needed
       json.put("start", start);
       json.put("end", end);
       json.put("tags", "");
       json.put("docfolder", "");
       json.put("page", "calendar");
       
       if (withRecurrence)
       {
          json.put("recurrenceRule", "FREQ=WEEKLY;INTERVAL=2;BYDAY=WE,FR");
          json.put("recurrenceLastMeeting", "2011-09-11");
       }
       
       Response response = sendRequest(new PutRequest(URL_EVENT_BASE + name, json.toString(), "application/json"), expectedStatus);
       if (expectedStatus == Status.STATUS_OK)
       {
          JSONObject result = validateAndParseJSON(response.getContentAsString());
          if (result.has("event"))
          {
             return result.getJSONObject("event");
          }
          if (result.has("data"))
          {
             return result.getJSONObject("data");
          }
          return result;
       }
       else
       {
          return null;
       }
    }
    
    /**
     * Gets the event name (ics timestamp based) from an entry
     */
    private String getNameFromEntry(JSONObject entry) throws Exception
    {
       if (! entry.has("uri"))
       {
          throw new IllegalArgumentException("No uri in " + entry.toString());
       }
    
       String uri = entry.getString("uri");
       String name = uri.substring( 
             uri.indexOf(SITE_SHORT_NAME_CALENDAR) + SITE_SHORT_NAME_CALENDAR.length() + 1);
       
       if (name.indexOf('?') > 0)
       {
          return name.substring(0, name.indexOf('?'));
       }
       else
       {
          return name;
       }
    }
    
    private String getEntriesICAL() throws Exception
    {
       Response response = sendRequest(new GetRequest(URL_EVENTS_LIST_ICS), 200);
       return response.getContentAsString();
    }
    
    
    // Tests
    
    /**
     * Creating, editing, fetching and deleting an entry
     */
    public void testCreateEditDeleteEntry() throws Exception
    {
       JSONObject entry;
       String name;
       
       
       // Won't be there to start with
       entry = getEntry(EVENT_TITLE_ONE, Status.STATUS_OK);
       assertEquals(true, entry.has("error"));
       
       
       // Create
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", Status.STATUS_OK);
       name = getNameFromEntry(entry);
       
       assertEquals(EVENT_TITLE_ONE, entry.getString("name"));
       assertEquals("Where", entry.getString("where"));
       assertEquals("Thing", entry.getString("desc"));
       assertEquals("2011-06-29", entry.getJSONObject("startAt").getString("legacyDate")); // Different format!
       assertEquals("2011-06-29", entry.getJSONObject("endAt").getString("legacyDate")); // Different format!
       assertEquals("12:00", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("13:00", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       // No isoutlook on create/edit
       
       
       // Fetch
       entry = getEntry(name, Status.STATUS_OK);
       
       assertEquals("Error found " + entry.toString(), false, entry.has("error"));
       assertEquals(EVENT_TITLE_ONE, entry.getString("what"));
       assertEquals(name, entry.getString("name"));
       assertEquals("Where", entry.getString("location")); // Not where...
       assertEquals("Thing", entry.getString("description")); // Not desc...
       
       assertEquals("false", entry.getString("isoutlook"));
       assertEquals("6/29/2011", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("6/29/2011", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("12:00", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("13:00", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       
       // Check the new style dates too
       assertEquals("2011-06-29T12:00:00.000+01:00", entry.getJSONObject("startAt").get("iso8601"));
       assertEquals("6/29/2011", entry.getJSONObject("startAt").get("legacyDate"));
       assertEquals("12:00", entry.getJSONObject("startAt").get("legacyTime"));
       assertEquals("2011-06-29T13:00:00.000+01:00", entry.getJSONObject("endAt").get("iso8601"));
       assertEquals("6/29/2011", entry.getJSONObject("endAt").get("legacyDate"));
       assertEquals("13:00", entry.getJSONObject("endAt").get("legacyTime"));
       
       
       // Edit
       entry = updateEntry(name, EVENT_TITLE_ONE, "More Where", "More Thing", false, Status.STATUS_OK);
       assertEquals("Error found " + entry.toString(), false, entry.has("error"));
       assertEquals(EVENT_TITLE_ONE, entry.getString("summary"));
       assertEquals("More Where", entry.getString("location"));
       assertEquals("More Thing", entry.getString("description"));
       
       // Now uses new style dates
       assertEquals("2011-06-28T11:30", entry.getJSONObject("startAt").getString("legacyDateTime"));
       assertEquals("2011-06-28T13:30", entry.getJSONObject("endAt").getString("legacyDateTime"));
       assertEquals("false", entry.getString("allday"));
       // No isoutlook on create/edit
       
       
       // Fetch
       entry = getEntry(name, Status.STATUS_OK);
       
       assertEquals("Error found " + entry.toString(), false, entry.has("error"));
       assertEquals(EVENT_TITLE_ONE, entry.getString("what"));
       assertEquals(name, entry.getString("name"));
       assertEquals("More Where", entry.getString("location")); // Not where...
       assertEquals("More Thing", entry.getString("description"));
       
       assertEquals("false", entry.getString("isoutlook"));
       assertEquals("6/28/2011", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("6/28/2011", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("11:30", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("13:30", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));

       
       // TODO Make it a whole day event and check that
       
       
       // Make it recurring
       entry = updateEntry(name, EVENT_TITLE_ONE, "More Where", "More Thing", true, Status.STATUS_OK);       
       
       // Fetch
       entry = getEntry(name, Status.STATUS_OK);
       
       assertEquals("Error found " + entry.toString(), false, entry.has("error"));
       assertEquals(EVENT_TITLE_ONE, entry.getString("what"));
       assertEquals(name, entry.getString("name"));
       assertEquals("More Where", entry.getString("location")); // Not where...
       assertEquals("More Thing", entry.getString("description"));
       
       assertEquals("false", entry.getString("isoutlook"));
       assertEquals("6/28/2011", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("6/28/2011", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("11:30", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("13:30", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       assertEquals(
             "Occurs every 2 weeks on Wednesday, Friday, effective " +
             "28-Jun-2011 until 11-Sep-2011 from 11:30 to 13:30", 
             entry.getString("recurrence"));
       
       // Delete
       sendRequest(new DeleteRequest(URL_EVENT_BASE + name), Status.STATUS_NO_CONTENT);
       
       
       // Fetch, will have gone
       entry = getEntry(EVENT_TITLE_ONE, Status.STATUS_OK);
       assertEquals(true, entry.has("error"));
       
       
       // Can't delete again
       sendRequest(new DeleteRequest(URL_EVENT_BASE + name), Status.STATUS_NOT_FOUND);
       
       // Can't edit it when it's deleted
       sendRequest(new PutRequest(URL_EVENT_BASE + name, "{}", "application/json"), Status.STATUS_OK);
       assertEquals(true, entry.has("error"));
    }
    
    /**
     * Checks that we can work with old style two part date times,
     *  ISO8601 datetimes with offsets, and ISO8601 datetimes with an
     *  explicit timezone. 
     */
    public void testDifferentDateStyles() throws Exception
    {
       JSONObject json;
       JSONObject entry;
       JSONObject startAt;
       JSONObject endAt;
       
       
       // From+Start, To+End with slashes
       json = new JSONObject();
       json.put("from", "2011/06/21");
       json.put("to",   "2011/06/21");
       json.put("start", "11:00");
       json.put("end",   "12:00");
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       assertEquals("2011-06-21", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-21", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("11:00", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("12:00", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       // Can't check iso8601 form as we don't know the TZ
       
       
       // From+Start, To+End with dashes
       json = new JSONObject();
       json.put("from", "2011-06-22");
       json.put("to",   "2011-06-22");
       json.put("start", "10:00");
       json.put("end",   "12:00");
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       assertEquals("2011-06-22", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-22", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("10:00", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("12:00", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       // Can't check iso8601 form as we don't know the TZ
       
       
       // ISO8601 with offset
       json = new JSONObject();
       json.put("startAt", "2011-06-21T11:30:05+01:00");
       json.put("endAt",   "2011-06-21T12:45:25+01:00");
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       // Check old style dates and times
       assertEquals("2011-06-21", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-21", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("11:30", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("12:45", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       
       // Check new style dates and times
       assertEquals("2011-06-21T11:30:05.000+01:00", entry.getJSONObject("startAt").getString("iso8601"));
       assertEquals("2011-06-21T12:45:25.000+01:00", entry.getJSONObject("endAt").getString("iso8601"));
       
       
       // ISO8601 with timezone
       json = new JSONObject();
       json.put("startAt", "2011-06-22T11:30:05");
       json.put("endAt",   "2011-06-22T12:45:25");
       json.put("timeZone", "Europe/London");
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       // Check old style dates and times
       assertEquals("2011-06-22", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-22", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("11:30", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("12:45", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       
       // Check new style dates and times
       assertEquals("2011-06-22T11:30:05.000+01:00", entry.getJSONObject("startAt").getString("iso8601"));
       assertEquals("2011-06-22T12:45:25.000+01:00", entry.getJSONObject("endAt").getString("iso8601"));
       
       
       // ISO8601 in get style, with offset
       json = new JSONObject();
       startAt = new JSONObject();
       endAt = new JSONObject();
       startAt.put("iso8601",  "2011-06-20T09:35:05+01:00");
       endAt.put("iso8601",  "2011-06-20T10:35:25+01:00");
       json.put("startAt", startAt);
       json.put("endAt",   endAt);
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       // Check old style dates and times
       assertEquals("2011-06-20", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-20", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("09:35", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("10:35", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       
       // Check new style dates and times
       assertEquals("2011-06-20T09:35:05.000+01:00", entry.getJSONObject("startAt").getString("iso8601"));
       assertEquals("2011-06-20T10:35:25.000+01:00", entry.getJSONObject("endAt").getString("iso8601"));
       
       
       // ISO8601 in get style, with timezone
       json = new JSONObject();
       startAt = new JSONObject();
       endAt = new JSONObject();
       startAt.put("iso8601",  "2011-06-24T09:30:05");
       startAt.put("timeZone", "Europe/London");
       endAt.put("iso8601",  "2011-06-24T10:45:25");
       endAt.put("timeZone", "Europe/London");
       json.put("startAt", startAt);
       json.put("endAt",   endAt);
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       // Check old style dates and times
       assertEquals("2011-06-24", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-24", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("09:30", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("10:45", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("false", entry.getString("allday"));
       
       // Check new style dates and times
       assertEquals("2011-06-24T09:30:05.000+01:00", entry.getJSONObject("startAt").getString("iso8601"));
       assertEquals("2011-06-24T10:45:25.000+01:00", entry.getJSONObject("endAt").getString("iso8601"));
       
       
       // All-day old-style
       json = new JSONObject();
       json.put("from", "2011/06/21");
       json.put("to",   "2011/06/21");
       json.put("allday", Boolean.TRUE);
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);

       assertEquals("2011-06-21", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-21", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("00:00", entry.getJSONObject("startAt").getString("legacyTime"));
       assertEquals("00:00", entry.getJSONObject("endAt").getString("legacyTime"));
       assertEquals("true", entry.getString("allday"));

       
       // All-day ISO8601 with offset
       json = new JSONObject();
       json.put("startAt", "2011-06-21T00:00:00+01:00");
       json.put("endAt",   "2011-06-21T00:00:00+01:00");
       json.put("allday", Boolean.TRUE);
       
       assertEquals("2011-06-21", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-21", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("true", entry.getString("allday"));

       
       // All-day ISO8601 with timezone
       json = new JSONObject();
       json.put("startAt", "2011-06-22T00:00:00");
       json.put("endAt",   "2011-06-22T00:00:00");
       json.put("timeZone", "Europe/London");
       json.put("allday", Boolean.TRUE);
       entry = createEntry(EVENT_TITLE_ONE, "Where", "Thing", json, Status.STATUS_OK);
       
       assertEquals("2011-06-22", entry.getJSONObject("startAt").getString("legacyDate"));
       assertEquals("2011-06-22", entry.getJSONObject("endAt").getString("legacyDate"));
       assertEquals("true", entry.getString("allday"));
    }
    
    /**
     * Listing
     */
    public void testOverallListing() throws Exception
    {
       JSONObject dates;
       JSONArray entries;
       
       // Initially, there are no events
       dates = getEntries(null, null);
       assertEquals(0, dates.length());
       
       
       // Add two events in the past
       createEntry(EVENT_TITLE_ONE, "Somewhere", "Thing 1", Status.STATUS_OK);
       createEntry(EVENT_TITLE_TWO, "Somewhere", "Thing 2", Status.STATUS_OK);
       
       // Check again
       dates = getEntries(null, null);
       
       // Should have two entries on the one day
       assertEquals(1, dates.length());
       assertEquals("6/29/2011", dates.names().getString(0));
     
       entries = dates.getJSONArray("6/29/2011");
       assertEquals(2, entries.length());
       assertEquals(EVENT_TITLE_ONE, entries.getJSONObject(0).getString("name"));
       assertEquals(EVENT_TITLE_TWO, entries.getJSONObject(1).getString("name"));
       
       
       // Add a third, on the next day
       JSONObject entry = createEntry(EVENT_TITLE_THREE, "Where3", "Thing 3", Status.STATUS_OK);
       String name3 = getNameFromEntry(entry);
       updateEntry(name3, EVENT_TITLE_THREE, "More Where 3", "More Thing 3", false, Status.STATUS_OK);
       
       
       // Check now, should have two days
       dates = getEntries(null, null);
       assertEquals(2, dates.length());
       assertEquals("6/29/2011", dates.names().getString(0));
       assertEquals("6/28/2011", dates.names().getString(1));
       
       entries = dates.getJSONArray("6/29/2011");
       assertEquals(2, entries.length());
       assertEquals(EVENT_TITLE_ONE, entries.getJSONObject(0).getString("name"));
       assertEquals(EVENT_TITLE_TWO, entries.getJSONObject(1).getString("name"));
       
       entries = dates.getJSONArray("6/28/2011");
       assertEquals(1, entries.length());
       assertEquals(EVENT_TITLE_THREE, entries.getJSONObject(0).getString("name"));
       
       
       // Now check the ICS
       String ical = getEntriesICAL();
       assertTrue("Invalid ICAL:\n" + ical, ical.contains("BEGIN:VCALENDAR"));
       assertTrue("Invalid ICAL:\n" + ical, ical.contains("END:VCALENDAR"));
       
       // Should have the three entries
       assertEquals(3+1, ical.split("BEGIN:VEVENT").length);
       
       assertTrue("Title not found:\n" + ical, ical.contains("SUMMARY:"+EVENT_TITLE_ONE));
       assertTrue("Title not found:\n" + ical, ical.contains("SUMMARY:"+EVENT_TITLE_TWO));
       assertTrue("Title not found:\n" + ical, ical.contains("SUMMARY:"+EVENT_TITLE_THREE));
    }
    
    /**
     * Listing for a user
     */
    public void testUserListing() throws Exception
    {
       JSONObject result;
       JSONArray events;
       
       // Initially, there are no events
       result = getEntries(null, null);
       assertEquals(0, result.length());
       
       result = getEntries("admin", null);
       events = result.getJSONArray("events");
       assertEquals(0, events.length());
       
       // Add two events in the past
       createEntry(EVENT_TITLE_ONE, "Somewhere", "Thing 1", Status.STATUS_OK);
       createEntry(EVENT_TITLE_TWO, "Somewhere", "Thing 2", Status.STATUS_OK);
       
       // Check again
       result = getEntries(null, null);
       assertEquals(1, result.length());
       
       result = getEntries("admin", "2000/01/01"); // With a from date
       events = result.getJSONArray("events");
       assertEquals(2, events.length());
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(1).getString("title"));
       
       result = getEntries("admin", null); // Without a from date
       events = result.getJSONArray("events");
       assertEquals(2, events.length());
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(1).getString("title"));
       
       
       // Add a third, on the next day
       JSONObject entry = createEntry(EVENT_TITLE_THREE, "Where3", "Thing 3", Status.STATUS_OK);
       String name3 = getNameFromEntry(entry);
       updateEntry(name3, EVENT_TITLE_THREE, "More Where 3", "More Thing 3", false, Status.STATUS_OK);
       
       
       // Check getting all of them
       result = getEntries("admin", null);
       events = result.getJSONArray("events");
       assertEquals(3, events.length());
       assertEquals(EVENT_TITLE_THREE, events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(1).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(2).getString("title"));
       assertEquals(SITE_SHORT_NAME_CALENDAR, events.getJSONObject(0).getString("site"));
       assertEquals(SITE_SHORT_NAME_CALENDAR, events.getJSONObject(1).getString("site"));
       assertEquals(SITE_SHORT_NAME_CALENDAR, events.getJSONObject(2).getString("site"));
       

       // Now set a date filter to constrain
       result = getEntries("admin", "2011/06/29");
       events = result.getJSONArray("events");
       assertEquals(2, events.length());
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(1).getString("title"));
       
       result = getEntries("admin", "2011/07/01");
       events = result.getJSONArray("events");
       assertEquals(0, events.length());
       
       
       // Make it not site specific
       Response response = sendRequest(new GetRequest(URL_USER_EVENTS_LIST + "?from=2000/01/01"), 200);
       result = new JSONObject(response.getContentAsString());
       events = result.getJSONArray("events");
       assertEquals(3, events.length());
       
       
       // Now hide the site, and remove the user from it, events will go
       this.authenticationComponent.setCurrentUser(AuthenticationUtil.getAdminUserName());
       SiteInfo site = siteService.getSite(SITE_SHORT_NAME_CALENDAR);
       site.setVisibility(SiteVisibility.PRIVATE);
       siteService.updateSite(site);
       siteService.removeMembership(SITE_SHORT_NAME_CALENDAR, USER_ONE);
       this.authenticationComponent.setCurrentUser(USER_ONE);
       
       response = sendRequest(new GetRequest(URL_USER_EVENTS_LIST + "?from=2000/01/01"), 200);
       result = new JSONObject(response.getContentAsString());
       events = result.getJSONArray("events");
       assertEquals(0, events.length());
    }

    /**
     * Repeating events support
     */
    public void testRepeatingEventsInUserListing() throws Exception 
    {
       JSONObject result;
       JSONArray events;
       
       // Initially, there are no events
       result = getEntries(null, null);
       assertEquals(0, result.length());
       
       result = getEntries("admin", null);
       events = result.getJSONArray("events");
       assertEquals(0, events.length());
       
       // Add two events in the past, one of which repeats 
       JSONObject entry1 = createEntry(EVENT_TITLE_ONE, "Somewhere", "Thing 1", Status.STATUS_OK);
       JSONObject entry2 = createEntry(EVENT_TITLE_TWO, "Somewhere", "Thing 2", Status.STATUS_OK);
       String entryName1 = getNameFromEntry(entry1); 
       String entryName2 = getNameFromEntry(entry2);
       // Have it repeat on wednesdays and fridays every two weeks
       updateEntry(entryName2, EVENT_TITLE_TWO, "Somewhere", "Thing 2", true, Status.STATUS_OK);
       
       
       // Get all the entries, without repeats expanded
       result = getEntries("admin", "2011-06-27");
       events = result.getJSONArray("events");
       assertEquals(2, events.length());
       assertEquals(entryName2, events.getJSONObject(0).getString("name"));
       assertEquals(entryName1, events.getJSONObject(1).getString("name"));
       assertEquals(EVENT_TITLE_TWO + " (Repeating)", events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(1).getString("title"));
       
       
       // Get all the entries, with repeats expanded
       result = getEntries("admin", "2011/06/27");
       events = result.getJSONArray("events");
       assertEquals(7, Math.min(events.length(),7)); // At least
       assertEquals(entryName2, events.getJSONObject(0).getString("name"));
       assertEquals(entryName2, events.getJSONObject(1).getString("name")); // 11:30 ->
       assertEquals(entryName1, events.getJSONObject(2).getString("name")); // 12:00 ->
       assertEquals(entryName2, events.getJSONObject(3).getString("name"));
       assertEquals(entryName2, events.getJSONObject(4).getString("name"));
       assertEquals(entryName2, events.getJSONObject(5).getString("name"));
       assertEquals(entryName2, events.getJSONObject(6).getString("name"));
       assertEquals(entryName2, events.getJSONObject(7).getString("name"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(0).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(1).getString("title"));
       assertEquals(EVENT_TITLE_ONE, events.getJSONObject(2).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(3).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(4).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(5).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(6).getString("title"));
       assertEquals(EVENT_TITLE_TWO, events.getJSONObject(7).getString("title"));
       
       // Check the dates on these:
       // Repeating original
       assertEquals("2011-06-28T", events.getJSONObject(0).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 1st repeat Wednesday
       assertEquals("2011-06-29T", events.getJSONObject(1).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // Non-repeating original
       assertEquals("2011-06-29T", events.getJSONObject(2).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 1st repeat Friday
       assertEquals("2011-07-01T", events.getJSONObject(3).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 2nd repeat Wednesday
       assertEquals("2011-07-13T", events.getJSONObject(4).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 2nd repeat Friday
       assertEquals("2011-07-15T", events.getJSONObject(5).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 3rd repeat Wednesday
       assertEquals("2011-07-27T", events.getJSONObject(6).getJSONObject("startAt").getString("iso8601").substring(0,11));
       // 3rd repeat Friday
       assertEquals("2011-07-29T", events.getJSONObject(7).getJSONObject("startAt").getString("iso8601").substring(0,11));
    }
}