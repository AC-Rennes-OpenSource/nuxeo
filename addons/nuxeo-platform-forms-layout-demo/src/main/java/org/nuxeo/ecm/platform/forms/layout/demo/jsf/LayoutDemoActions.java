/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.forms.layout.demo.jsf;

import static org.jboss.seam.ScopeType.EVENT;
import static org.jboss.seam.ScopeType.SESSION;

import java.io.Serializable;

import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetTypeConfiguration;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetTypeDefinition;
import org.nuxeo.ecm.platform.forms.layout.demo.service.DemoWidgetType;
import org.nuxeo.ecm.platform.forms.layout.demo.service.LayoutDemoManager;
import org.nuxeo.ecm.platform.forms.layout.service.WebLayoutManager;
import org.nuxeo.ecm.platform.url.api.DocumentView;

/**
 * Seam component providing a document model for layout demo and testing, and
 * handling reset of this document model when the page changes.
 *
 * @author Anahide Tchertchian
 */
@Name("layoutDemoActions")
@Scope(SESSION)
public class LayoutDemoActions implements Serializable {

    private static final long serialVersionUID = 1L;

    @In(create = true)
    protected LayoutDemoManager layoutDemoManager;

    @In(create = true)
    protected WebLayoutManager webLayoutManager;

    @In(create = true)
    protected DocumentModel layoutBareDemoDocument;

    protected DocumentModel layoutDemoDocument;

    protected DemoWidgetType currentWidgetType;

    protected String currentTabId;

    protected String currentSubTabId;

    protected PreviewLayoutDefinition viewPreviewLayoutDef;

    protected PreviewLayoutDefinition editPreviewLayoutDef;

    @Factory(value = "layoutDemoDocument", scope = EVENT)
    public DocumentModel getDemoDocument() throws ClientException {
        if (layoutDemoDocument == null) {
            try {
                layoutDemoDocument = layoutBareDemoDocument.clone();
            } catch (Exception e) {
                throw new ClientException(e);
            }
        }
        return layoutDemoDocument;
    }

    public String initContextFromRestRequest(DocumentView docView)
            throws ClientException {

        DemoWidgetType widgetType = null;
        boolean isPreviewFrame = false;
        if (docView != null) {
            String viewId = docView.getViewId();
            if (viewId != null) {
                // try to deduce current widget type
                widgetType = layoutDemoManager.getWidgetTypeByViewId(viewId);
            }
        }

        if (!isPreviewFrame) {
            // avoid resetting contextual info when generating preview frame
            setCurrentWidgetType(widgetType);
        }

        return null;
    }

    public void setCurrentWidgetType(DemoWidgetType newWidgetType) {
        if (currentWidgetType != null
                && !currentWidgetType.equals(newWidgetType)) {
            // reset demo doc too
            layoutDemoDocument = null;
            viewPreviewLayoutDef = null;
            editPreviewLayoutDef = null;
            currentTabId = null;
            currentSubTabId = null;
        }
        currentWidgetType = newWidgetType;
    }

    @Factory(value = "currentWidgetType", scope = EVENT)
    public DemoWidgetType getCurrentWidgetType() {
        return currentWidgetType;
    }

    @Factory(value = "currentWidgetTypeDef", scope = EVENT)
    public WidgetTypeDefinition getCurrentWidgetTypeDefinition() {
        if (currentWidgetType != null) {
            String type = currentWidgetType.getName();
            return webLayoutManager.getWidgetTypeDefinition(type);
        }
        return null;
    }

    @Factory(value = "currentWidgetTypeConf", scope = EVENT)
    public WidgetTypeConfiguration getCurrentWidgetTypeConfiguration() {
        WidgetTypeDefinition def = getCurrentWidgetTypeDefinition();
        if (def != null) {
            return def.getConfiguration();
        }
        return null;
    }

    @Factory(value = "layoutDemoCurrentTabId", scope = EVENT)
    public String getCurrentTabId() {
        return currentTabId;
    }

    public void setCurrentTabId(String currentTabId) {
        this.currentTabId = currentTabId;
    }

    @Factory(value = "layoutDemoCurrentSubTabId", scope = EVENT)
    public String getCurrentSubTabId() {
        return currentSubTabId;
    }

    public void setCurrentSubTabId(String currentSubTabId) {
        this.currentSubTabId = currentSubTabId;
    }

    protected PreviewLayoutDefinition createPreviewLayoutDefinition(
            DemoWidgetType widgetType) {
        PreviewLayoutDefinition def = new PreviewLayoutDefinition(
                widgetType.getName(), widgetType.getFields(),
                widgetType.getDefaultProperties());
        // set a custom label and help label
        def.setLabel("My widget label");
        def.setHelpLabel("My widget help label");
        return def;
    }

    @Factory(value = "viewPreviewLayoutDef", scope = EVENT)
    public PreviewLayoutDefinition getViewPreviewLayoutDefinition() {
        if (viewPreviewLayoutDef == null && currentWidgetType != null) {
            viewPreviewLayoutDef = createPreviewLayoutDefinition(currentWidgetType);
        }
        return viewPreviewLayoutDef;
    }

    @Factory(value = "editPreviewLayoutDef", scope = EVENT)
    public PreviewLayoutDefinition getEditPreviewLayoutDefinition() {
        if (editPreviewLayoutDef == null && currentWidgetType != null) {
            editPreviewLayoutDef = createPreviewLayoutDefinition(currentWidgetType);
        }
        return editPreviewLayoutDef;
    }

}
