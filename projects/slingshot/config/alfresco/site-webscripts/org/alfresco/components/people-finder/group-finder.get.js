// Widget instantiation metadata...
var searchConfig = config.scoped['Search']['search'],
    defaultMinSearchTermLength = searchConfig.getChildValue('min-search-term-length'),
    defaultMaxSearchResults = searchConfig.getChildValue('max-search-results');

model.widgets = [];
var groupFinder = {};
groupFinder.name = "Alfresco.GroupFinder";
groupFinder.useMessages = true;
groupFinder.useOptions = true;
groupFinder.options = {};
groupFinder.options.siteId = (this.page != null) ? ((this.page.url.templateArgs.site != null) ? this.page.url.templateArgs.site : "") : ((args.site != null) ? args.site : "");
groupFinder.options.minSearchTermLength = (args.minSearchTermLength != null) ? args.minSearchTermLength : defaultMinSearchTermLength;
groupFinder.options.maxSearchResults = (args.maxSearchResults != null) ? args.maxSearchResults : defaultMaxSearchResults;
groupFinder.options.setFocus = (args.setFocus != null) ? args.setFocus : "false";
groupFinder.options.addButtonSuffix = (args.addButtonSuffix != null) ? args.addButtonSuffix : "";
groupFinder.options.dataWebScript = ((args.dataWebScript != null) ? args.dataWebScript : "api/groups").replace(/{/g, "[").replace(/}/g, "]");
model.widgets.push(groupFinder);