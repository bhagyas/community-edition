<%--
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
--%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/alfresco.tld" prefix="a" %>
<%@ taglib uri="/WEB-INF/repo.tld" prefix="r" %>

<%@ page import="org.alfresco.web.ui.common.PanelGenerator" %>

<f:verbatim>	
<table cellpadding="2" cellspacing="2" border="0" width="100%">
 <tr>
    <td class="mainSubTitle">
       </f:verbatim><h:outputText value="#{msg.edit_file_title}" /><f:verbatim>
    </td>
 </tr>
 <tr>
    <td class="mainSubText">
       </f:verbatim><h:outputFormat value="#{msg.edit_file_prompt}">
          <f:param value="#{CCProperties.document.name}" />
       </h:outputFormat><f:verbatim>
    </td>
 </tr>
 <tr>
    <td style="padding:10px" valign="middle">
       <%-- downloadable file link generated by CheckinCheckoutDialog --%>
       </f:verbatim><a:actionLink styleClass="title" image="#{CCProperties.document.properties.fileType32}" value="#{CCProperties.document.name}" href="#{CCProperties.document.properties.url}" /><f:verbatim>
    </td>
 </tr>
 <tr>
    <td>
       </f:verbatim><h:outputText value="#{msg.edit_download_complete}" /><f:verbatim>
    </td>
 </tr>
 
 <%-- Hide the checkout info if this document is already checked out --%>
 </f:verbatim><a:panel id="checkout-panel" rendered="#{CCProperties.document.properties.workingCopy == false}"><f:verbatim>
    <tr><td class="paddingRow"></td></tr>
    <tr>
       <td class="mainSubTitle">
          </f:verbatim><h:outputText value="#{msg.checkout_file_title}" /><f:verbatim>
       </td>
    </tr>
    <tr>
       <td>
          </f:verbatim><h:outputText value="#{msg.checkout_you_may_want}" /><f:verbatim>
          <div class="mainSubText" style="padding-top:6px;padding-left:2px">
             </f:verbatim><a:actionLink value="#{msg.checkout}" image="/images/icons/CheckOut_icon.gif" actionListener="#{CCEditFileDialog.setupContentAction}" action="dialog:checkoutFile">
                <f:param name="id" value="#{CCProperties.document.id}" />
                <f:param name="action" value="CHECKOUT_FILE" />
             </a:actionLink><f:verbatim>
          </div>
          <div class="mainSubText" style="padding-top:6px"></f:verbatim><h:outputText value="#{msg.checkout_hint1}" /><f:verbatim></div>
          <div class="mainSubText"></f:verbatim><h:outputText value="#{msg.checkout_hint2}" /><f:verbatim></div>
       </td>
    </tr>	
 </f:verbatim></a:panel><f:verbatim>
</table>
</f:verbatim>