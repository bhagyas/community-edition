define(["alfresco/core/ProcessWidgets",
        "dojo/_base/declare",
        "dojo/dom-construct",
        "dojo/dom-style"], 
        function(ProcessWidgets, declare, domConstruct, domStyle) {
   
   return declare([ProcessWidgets], {
      
      /**
       * The scope to use for i18n messages.
       * 
       * @property i18nScope {String}
       */
      i18nScope: "org.alfresco.js.HorizontalWidgets",
      
      /**
       * The CSS class (or a space separated list of classes) to include in the DOM node.
       * 
       * @property {string} baseClass
       * @default "horizontal-widgets"
       */
      baseClass: "horizontal-widgets",
      
      /**
       * This will be set to a percentage value such that each widget displayed has an equal share
       * of page widgth. 
       * 
       * @property {string} widgetWidth
       * @default null 
       */
      widgetWidth: null,
      
      /**
       * Sets up the default width to be allocated to each child widget to be added.
       * 
       * @method postCreate
       */
      postCreate: function alfresco_layout_HorizontalWidgets__postCreate() {
         // Split the full width between all widgets... 
         // We should update this to allow for specific widget width requests...
         if (this.widgets)
         {
            this.widgetWidth = 100 / this.widgets.length; 
         }
         this.inherited(arguments);
      },
      
      /**
       * This overrides the default implementation to ensure that each each child widget added has the 
       * appropriate CSS classes applied such that they appear horizontally. It also sets the width
       * of each widget appropriately (either based on the default generated width which is an equal
       * percentage assigned to each child widget) or the specific width configured for the widget.
       * 
       * @method createWidgetDomNode
       * @param {object} widget The definition of the widget to create the DOM node for.
       * @returns {element} A new DOM node for the widget to be attached to
       */
      createWidgetDomNode: function alfresco_layout_HorizontalWidgets__createWidgetDomNode(widget) {
         var outerDiv = domConstruct.create("div", { className: "horizontal-widget"}, this.containerNode);
         
         var width = this.widgetWidth + "%";
        
         if (widget.config && widget.config.width)
         {
            width = widget.config.width;
         }
         // Set the width of each widget according to how many there are...
         domStyle.set(outerDiv, {
            "width" : width
         });
         
         var innerDiv = domConstruct.create("div", {}, outerDiv);
         return innerDiv;
      }
   });
});