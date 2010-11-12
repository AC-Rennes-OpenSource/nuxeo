package org.nuxeo.apidoc.seam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.jboss.seam.Component;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.contexts.ServletLifecycle;
import org.jboss.seam.web.ServletContexts;
import org.nuxeo.apidoc.api.SeamComponentInfo;
import org.nuxeo.apidoc.introspection.SeamComponentInfoImpl;

public class SeamRuntimeIntrospector {

    protected static List<String> listAllComponentsNames() {
        List<String> names = new ArrayList<String>();
        if (Contexts.isApplicationContextActive()) {
            for (String name : Contexts.getApplicationContext().getNames()) {
                if (name.endsWith(".component")) {
                    names.add(name.replace(".component", ""));
                }
            }
        }
        return names;
    }

    public static List<SeamComponentInfo> listNuxeoComponents(HttpServletRequest request) {

    ServletLifecycle.beginRequest(request);
    ServletContexts.instance().setRequest(request);
    //ConversationPropagation.instance().setConversationId( conversationId );
    //Manager.instance().restoreConversation();
    //ServletLifecycle.resumeConversation(request);

    try {
        return listNuxeoComponents();
    }
    finally {
        ServletLifecycle.endRequest(request);
    }

    }

    protected static List<SeamComponentInfo> components=null;

    protected static synchronized List<SeamComponentInfo> listNuxeoComponents() {
        if (components==null) {
            components = new ArrayList<SeamComponentInfo>();
            for(String cName : listAllComponentsNames()) {
                SeamComponentInfoImpl desc = new SeamComponentInfoImpl();
                Component comp = Component.forName(cName);
                String className = comp.getBeanClass().getName();
                //if (className.startsWith("org.nuxeo")) {
                if (!className.startsWith("org.jboss")) {
                    desc.setName(cName);
                    desc.setScope(comp.getScope().toString());
                    desc.setClassName(className);

                    Set<Class> ifaces = comp.getBusinessInterfaces();
                    if (ifaces!=null && ifaces.size()>0) {
                        for (Class iface : ifaces) {
                            desc.addInterfaceName(iface.getName());
                        }
                    }
                    desc.addInterfaceName(comp.getBeanClass().getName());
                    components.add(desc);
                }
            }
            Collections.sort(components);
        }
        return components;
    }
}
