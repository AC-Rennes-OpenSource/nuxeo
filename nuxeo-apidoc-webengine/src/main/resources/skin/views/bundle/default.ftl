<#setting url_escaping_charset="ISO-8859-1">
<@extends src="base.ftl">
<@block name="title">Bundle ${nxItem.id}</@block>

<@block name="right">
<#include "/docMacros.ftl">

<h1>Bundle <span class="componentTitle">${nxItem.id}</span></h1>

<h2>Documentation</h2>
${nxItem.documentationHtml}
<@viewSecDescriptions docsByCat=docs.getDocumentationItems(Context.getCoreSession()) title=false/>

<h2>Components</h2>
<#if nxItem.components?size == 0>
  No components.
<#else>
  <ul>
    <#list nxItem.components as component>
    <li><a href="${Root.path}/${distId}/viewComponent/${component.name}">${component.name}</a></li>
    </#list>
  </ul>
</#if>

<h2>Maven artifact</h2>
<table class="bundleInfo">
  <tr><td> file: </td><td> ${nxItem.fileName} </td></tr>
  <tr><td> groupId: </td><td> ${nxItem.artifactGroupId} </td></tr>
  <tr><td> artifactId: </td><td> ${nxItem.artifactId} </td></tr>
  <tr><td> version: </td><td> ${nxItem.artifactVersion} </td></tr>
</table>

<h2>Manifest</h2>
<span class="resourceToggle">View META-INF/MANIFEST.MF</span>
<div class="hiddenResource">
  <pre><code>${nxItem.manifest}</code></pre>
</div>

<@viewAdditionalDoc docsByCat=docs.getDocumentationItems(Context.getCoreSession())/>

</@block>
</@extends>
