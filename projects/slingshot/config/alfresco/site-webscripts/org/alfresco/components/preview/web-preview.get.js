<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

function getPluginConditions(xmlConfig)
{
   // Create a json representation of the conditions that will be used to decide which previewer that shall be used
   var pluginConditions = [], conditionNode, pluginNode, condition, plugin, attribute;
   if (xmlConfig && xmlConfig["plugin-conditions"])
   {
      for each (conditionNode in xmlConfig["plugin-conditions"].elements("condition"))
      {
         condition =
         {
            attributes: {},
            plugins: []
         };

         for each (attribute in conditionNode.attributes())
         {
            condition.attributes[attribute.name()] = attribute.text();
         }

         for each (pluginNode in conditionNode.elements("plugin"))
         {
            plugin =
            {
               name: pluginNode.text(),
               attributes: {}
            };
            for each (attribute in pluginNode.attributes())
            {
               plugin.attributes[attribute.name()] = attribute.text();
            }
            condition.plugins.push(plugin);
         }
         pluginConditions.push(condition);
      }
      return pluginConditions;
   }
}

function getDocumentNode(nodeRef, defaultValue)
{

   var metadata = AlfrescoUtil.getMetaData(nodeRef, {});
   if (metadata.properties)
   {
      var node = {},
         mcns = "{http://www.alfresco.org/model/content/1.0}",
         content = metadata.properties[mcns + "content"];

      node.name = metadata.properties[mcns + "name"];
      node.mimeType = metadata.mimetype;
      if (content)
      {
         var size = content.substring(content.indexOf("size=") + 5);
         size = size.substring(0, size.indexOf("|"));
         node.size = size;
      }
      else
      {
         node.size = "0";
      }
      node.thumbnailModifications = metadata.properties[mcns + "lastThumbnailModification"];
      if (node.thumbnailModifications == null)
      {
         node.thumbnailModifications = [];
      }
      
      node.thumbnails = AlfrescoUtil.getThumbnails(nodeRef);
      return node;
   }
   else
   {
      return defaultValue;
   }
}

function main()
{
   // Populate model with parameters
   AlfrescoUtil.param("nodeRef");

   // Populate model with data from repo
   var pluginConditions; 
   var documentNode = getDocumentNode(model.nodeRef, null);
   if (documentNode)
   {
      // Populate model with data from node and config
      model.node = documentNode;
      pluginConditions = getPluginConditions(new XML(config.script));
      model.pluginConditionsJSON = jsonUtils.toJSONString(pluginConditions);
   }
   
   // Widget instantiation metadata...
   var webPreview = {
      id : "WebPreview", 
      name : "Alfresco.WebPreview",
      options : {
         thumbnailModification : model.node.thumbnailModifications,
         nodeRef : model.nodeRef,
         name : model.node.name,
         mimeType : model.node.mimeType,
         size: model.node.size,
         thumbnails : model.node.thumbnails,
         pluginConditions : pluginConditions
      }
   };
   model.widgets = [webPreview];
}

// Start the webscript
main();


// Set the group from the component property...
model.dependencyGroup =  (args.dependencyGroup != null) ? args.dependencyGroup : "web-preview";

