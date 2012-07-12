<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

function main()
{
   AlfrescoUtil.param('nodeRef');
   AlfrescoUtil.param('site', null);
   AlfrescoUtil.param('formId', null);
   var documentDetails = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);
   if (documentDetails)
   {
      model.document = documentDetails;
      model.allowMetaDataUpdate = documentDetails.item.node.permissions.user["Write"] || false;
   }
}

main();

// Widget instantiation metadata...
model.widgets = [];
var documentMetadata = {};
documentMetadata.name = "Alfresco.DocumentMetadata";
documentMetadata.useMessages = true;
documentMetadata.useOptions = true;
documentMetadata.options = {};
documentMetadata.options.nodeRef = model.nodeRef;
documentMetadata.options.siteId = model.site;
documentMetadata.options.formId = model.formId;
model.widgets.push(documentMetadata);