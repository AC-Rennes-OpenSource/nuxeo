<@extends src="base.ftl">

<@block name="stylesheets">
</@block>


<@block name="header_scripts">
</@block>

<@block name="right">

<h1>Welcome to Nuxeo Platform Explorer</h1>

<table class="welcome">
  <tr>
   <tr>
    <td colspan="2">
    
<h2>Nuxeo EP Distributions</h2>

<p>
Here are the currently available distributions:
</p>

<#assign rtSnap=Root.runtimeDistribution/>
<#assign snapMap=Root.persistedDistributions/>

<table class="tablesorter distributions">
<tr>
<th> Distribution name</th>
<th> Version</th>
<th> Creation date</th>
<th></th>
<th></th>
<th></th>
</tr>

<tr>
<td>${rtSnap.name}</td>
<td>${rtSnap.version}</td>
<td>${rtSnap.creationDate?datetime}</td>
<td style="color:green">Current deployed distribution (live) </td>

<td>
<#if Root.isEditor()>
  <form method="POST" action="${Root.path}/save">
  <input type="submit" value="Save">
  </form>
</#if>
</td>

<td>
  <p class="explore"><a href="${Root.path}/current/"> Explore </a></p>
</td>

</tr>

<#assign names=snapMap?keys/>
<#list names as name>
<tr><td colspan="6">&nbsp;</td></tr>
<tr>
  <#assign distrib=snapMap[name]/>
  <td>${distrib.name}</td>
  <td>${distrib.version}</td>
  <td>${distrib.creationDate?datetime}</td>
  <td>&nbsp;</td>
  <td>
    <p class="explore"><a href="${Root.path}/${distrib.key}/"> Explore </a></p>
  </td>
  <td>
    <p class="explore export"><A href="${Root.path}/download/${distrib.key}">Export</A> as zip.</p>
  </td>
</tr>
</#list>

</table>

<br/>
<#if Root.isEditor()>
<p> You can use the form below to upload a distribution that has been exported as a zip:
</p>
<form method="POST" action="${Root.path}/uploadDistrib" enctype="multipart/form-data" >
  <input type="file" name="archive">
  <input type="submit" value="Upload">
</form>
</#if>

    </td>
  </tr> 
    <td width="50%">
      <h2>What is Nuxeo Platform Explorer?</h2>
      <p>
        This application allows you to explore Nuxeo Enterprise Platform.
      </p>
      <p>
       You can explore the current live Nuxeo distribution (i.e. the one that runs this server) or browse a distribution that has been snapshotted and saved into local Document Repository.
      </p>
      <p>
        Keep in mind that only snapshotted distributions (i.e non-live) are stored as documents and therefore they are the only one to be searchable.
      </p>
    </td>
    <td width="50%">
    
<h2>Documentation</h2>

<p>
Documentation items are associated to the Nuxeo Platform artifacts.<br/>
Documentation packs can be downloaded or uploaded here.
</p>

<p>
Documentation contains currently ${Root.documentationInfo}
</p>

<a href="${Root.path}/downloadDoc">Export</a> all documentation as a zip.<br/><br/> <br/>

<br/>
<#if Root.isEditor()>
You can use the form below to upload a documentation pack (zip):<br/>
<form method="POST" action="${Root.path}/uploadDoc" enctype="multipart/form-data" >
  <input type="file" name="archive">
  <input type="submit" value="Upload doc pack">
</form>
</#if>
    
    </td>
  </tr>
</table>

</@block>

</@extends>