<#include "include/alfresco-template.ftl" />
<@templateHeader />

<@templateBody>
   <@markup id="alf-hd">
   <div id="alf-hd">
      <@region id="header" scope="global"/>
      <@region id="title" scope="template"/>
      <@region id="navigation" scope="template"/>
      <#if page.url.args.nodeRef??>
         <@region id="path" scope="template"/>
      </#if>
   </div>
   </@>
   <@markup id="bd">
   <div id="bd">
      <div class="share-form">
         <@region id="data-header" scope="page" />
         <@region id="data-form" scope="page" />
         <@region id="data-actions" scope="page" />
      </div>
   </div>
   </@>
   <#if page.url.args.nodeRef??>
   <@markup id="document-details">
      <script type="text/javascript">//<![CDATA[
      new Alfresco.DocumentDetails().setOptions(
      {
         nodeRef: new Alfresco.util.NodeRef("${page.url.args.nodeRef?js_string}"),
         siteId: "${page.url.templateArgs.site!""}",
         rootNode: "${(config.scoped["RepositoryLibrary"]["root-node"].getValue())!"alfresco://company/home"}"
      });
      //]]></script>
   </@>
   </#if>
</@>

<@templateFooter>
   <@markup id="alf-ft">
   <div id="alf-ft">
      <@region id="footer" scope="global"/>
      <@region id="data-loader" scope="page" />
   </div>
   </@>
</@>
