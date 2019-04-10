<h2> Implementation Class </h2>
 ${seamComponent.className}

<h2> Scope </h2>
 ${seamComponent.scope}

<h2> Implemented interfaces </h2>

     <#list seamComponent.interfaceNames as iface>
     	<br/>${iface}
     	<#assign javaDocBaseUrl="${Root.currentDistribution.javaDocHelper.getBaseUrl(iface)}"/>
     	<#assign javaDocUrl="${javaDocBaseUrl}/javadoc/${iface?replace('.','/')}.html"/>
	    &nbsp;&nbsp;&nbsp;  <span class="resourceToggle"> JavaDoc </span>
	    <div class="hiddenResource">
	      <A href="${javaDocUrl}" target="NxJavaDoc">Open in a new window</A>
	      <iframe src="${javaDocUrl}" width="98%" height="300px" border="0"></iframe>
	    </div>
     </#list>
