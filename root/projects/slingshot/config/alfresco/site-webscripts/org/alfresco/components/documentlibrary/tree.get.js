function widgets()
{
   var evaluateChildFolders = "true",
       maximumFolderCount = "-1";
   var docLibConfig = config.scoped["DocumentLibrary"];
   if (docLibConfig != null)
   {
      var tree = docLibConfig["tree"];
      if (tree != null)
      {
         var tmp = tree.getChildValue("evaluate-child-folders");
         evaluateChildFolders = tmp != null ? tmp : "true";
         tmp = tree.getChildValue("maximum-folder-count");
         maximumFolderCount = tmp != null ? tmp : "-1";
      }
   }

   var docListTree = {
      id : "DocListTree", 
      name : "Alfresco.DocListTree",
      options : {
         siteId : (page.url.templateArgs.site != null) ? page.url.templateArgs.site : "",
         containerId : template.properties.container != null ? template.properties.container : "documentLibrary",
         evaluateChildFolders : Boolean(evaluateChildFolders),
         maximumFolderCount : parseInt(maximumFolderCount),
         setDropTargets : true
      }
   };
   model.widgets = [docListTree];
}

widgets();
