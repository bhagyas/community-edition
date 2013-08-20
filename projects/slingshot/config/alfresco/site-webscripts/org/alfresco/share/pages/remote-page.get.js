var pageDefinition;
if (page.url.templateArgs.pagename != null)
{
   // If a page name has been supplied then retrieve it's details...
   // By passing the name only a single result should be returned...
   var json = remote.call("/remote-share/pages/name/" + page.url.templateArgs.pagename);
       pageDetails = null;
   try
   {
      if (json.status == 200)
      {
         pageDetails = jsonUtils.toObject(json.response);
      }
      else
      {
         model.jsonModelError = "remote.page.error.remotefailure";
      }
      if (pageDetails &&
          pageDetails.items != null &&
          pageDetails.items.size() == 1 &&
          pageDetails.items.get(0).content != null &&
          pageDetails.items.get(0).content != "")
      {
         pageDefinition = pageDetails.items.get(0).content;
      }
      else
      {
         model.jsonModelError = "remote.page.error.invalidData";
         model.jsonModelErrorArgs = pageDetails;
      }

      model.jsonModel = jsonUtils.toObject(pageDefinition);
   }
   catch(e)
   {
      model.jsonModelError = "remote.page.load.error";
      model.jsonModelErrorArgs = page.url.templateArgs.pagename;
   }
}
else
{
   // No page name supplied...
   model.jsonModelError = "remote.page.error.nopage"
}

