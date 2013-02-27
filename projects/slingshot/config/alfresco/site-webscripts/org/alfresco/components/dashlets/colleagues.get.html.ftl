<@markup id="css" >
   <#-- CSS Dependencies -->
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/components/dashlets/colleagues.css" group="dashlets"/>
</@>

<@markup id="js">
   <#-- No JavaScript Dependencies -->
</@>

<@markup id="widgets">
   <@createWidgets group="dashlets"/>
</@>

<@markup id="html">
   <@uniqueIdDiv>
      <div class="dashlet colleagues">
         <div class="title">${msg("header")}</div>
            <#if userMembership.isManager>
            <div class="toolbar flat-button">
               <div>
                  <span class="align-right yui-button-align">
                     <span class="first-child">
                        <a href="invite" class="theme-color-1">
                           <img src="${url.context}/res/components/images/user-16.png" style="vertical-align: text-bottom" width="16" />
                           ${msg("link.invite")}</a>
                     </span>
                  </span>
               </div>
            </div>
            </#if>
         <div class="toolbar flat-button">
            <div>
               <div class="align-left paginator">
                  ${msg("pagination.template", 1, memberships?size, totalResults?string)}
               </div>
               <span class="align-right yui-button-align">
                  <span class="first-child">
               <a href="site-members" class="theme-color-1">${msg("link.all-members")}</a>
                  </span>
               </span>
               <div class="clear"></div>
            </div>
         </div>
         <div class="body scrollableList" <#if args.height??>style="height: ${args.height}px;"</#if>>
            <#if (memberships?size == 1 && memberships[0].authority.userName = user.id)>
            <div class="info">
               <h3>${msg("empty.title")}</h3>
            </div>
            </#if>
            <#list memberships as m>
            <div class="detail-list-item">
               <div class="avatar">
                  <img src="${url.context}<#if m.authority.avatar??>/proxy/alfresco/${m.authority.avatar}<#else>/res/components/images/no-user-photo-64.png</#if>" alt="" />
               </div>
               <div class="person">
                  <h3><a href="${url.context}/page/user/${m.authority.userName?url}/profile" class="theme-color-1">${m.authority.firstName?html} <#if m.authority.lastName??>${m.authority.lastName?html}</#if></a></h3>
                  <div>${msg("role." + m.role)}</div>
                  <#if m.authority.userStatus??>
                     <div class="user-status">${(m.authority.userStatus!"")?html} <span class="time">(<span class="relativeTime">${(m.authority.userStatusTime.iso8601!"")?html}</span>)</span></div>
                  </#if>
               </div>
               <div class="clear"></div>
            </div>
            </#list>
         </div>
      </div>
      <script>Alfresco.util.renderRelativeTime("${args.htmlid?js_string}");</script>
   </@>
</@>