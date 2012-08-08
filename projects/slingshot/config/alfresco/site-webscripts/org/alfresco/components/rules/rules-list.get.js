function main()
{
   // Widget instantiation metadata...
   var rulesList = {
      id : "RulesList", 
      name : "Alfresco.RulesList",
      options : {
         siteId : (page.url.templateArgs.site != null) ? page.url.templateArgs.site : "",
         nodeRef : (page.url.args.nodeRef != null) ? page.url.args.nodeRef : "",
         filter : (args.filter != null) ? args.filter : "",
         selectDefault : Boolean((args.selectDefault != null) ? args.selectDefault : "false"),
         editable : Boolean((args.editable != null) ? args.editable : "false")
      }
   };
   model.widgets = [rulesList];
}

main();

