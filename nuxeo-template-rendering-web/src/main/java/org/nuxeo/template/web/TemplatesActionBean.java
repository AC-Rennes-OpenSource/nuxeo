package org.nuxeo.template.web;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.mvel2.optimizers.impl.refl.nodes.ArrayLength;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.types.Type;
import org.nuxeo.ecm.platform.types.TypeManager;
import org.nuxeo.ecm.webapp.contentbrowser.DocumentActions;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.template.api.TemplateInput;
import org.nuxeo.template.api.TemplateProcessorService;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;
import org.nuxeo.template.api.descriptor.TemplateProcessorDescriptor;

@Name("templateActions")
@Scope(CONVERSATION)
public class TemplatesActionBean extends BaseTemplateAction {

    private static final long serialVersionUID = 1L;

    @In(create = true)
    protected transient DocumentActions documentActions;

    @In(create = true)
    protected transient TypeManager typeManager;

    protected List<TemplateInput> templateInputs;

    protected List<TemplateInput> templateEditableInputs;

    protected TemplateInput newInput;

    public String createTemplate() throws Exception {
        DocumentModel changeableDocument = navigationContext.getChangeableDocument();
        TemplateSourceDocument sourceTemplate = changeableDocument.getAdapter(TemplateSourceDocument.class);
        if (sourceTemplate != null && sourceTemplate.getTemplateBlob() != null) {
            try {
                sourceTemplate.initTemplate(false);
                if (sourceTemplate.hasEditableParams()) {
                    templateInputs = sourceTemplate.getParams();
                    return "editTemplateRelatedData";
                }
            } catch (Exception e) {
                log.error("Error during parameter automatic initialization", e);
            }
        }
        return documentActions.saveDocument(changeableDocument);
    }

    public List<TemplateInput> getTemplateInputs() {
        return templateInputs;
    }

    public void setTemplateInputs(List<TemplateInput> templateInputs) {
        this.templateInputs = templateInputs;
    }

    public String saveDocument() throws Exception {
        DocumentModel changeableDocument = navigationContext.getChangeableDocument();

        for (TemplateInput ti : templateInputs) {
            log.info(ti.toString());
        }
        TemplateSourceDocument source = changeableDocument.getAdapter(TemplateSourceDocument.class);
        if (source != null) {
            source.saveParams(templateInputs, false);
        }

        return documentActions.saveDocument(changeableDocument);
    }

    @Observer(value = { EventNames.DOCUMENT_SELECTION_CHANGED,
            EventNames.NEW_DOCUMENT_CREATED, EventNames.DOCUMENT_CHANGED }, create = false)
    @BypassInterceptors
    public void reset() {
        templateInputs = null;
        templateEditableInputs = null;
    }

    public List<TemplateInput> getTemplateEditableInputs() throws Exception {
        if (templateEditableInputs == null) {
            DocumentModel currentDocument = navigationContext.getCurrentDocument();

            TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
            if (template != null) {
                templateEditableInputs = template.getParams();
            }
        }
        return templateEditableInputs;
    }

    public void setTemplateEditableInputs(
            List<TemplateInput> templateEditableInputs) {
        this.templateEditableInputs = templateEditableInputs;
    }

    public String saveTemplateInputs() throws Exception {

        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {
            currentDocument = template.saveParams(templateEditableInputs, true);
        }
        return navigationContext.navigateToDocument(currentDocument);
    }

    public TemplateInput getNewInput() {
        if (newInput == null) {
            newInput = new TemplateInput("newField");
        }
        return newInput;
    }

    public void setNewInput(TemplateInput newInput) {
        this.newInput = newInput;
    }

    public String addTemplateInput() throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {
            template.addInput(newInput);
            newInput = null;
            templateEditableInputs = null;
        } else {
            return null;
        }

        return navigationContext.navigateToDocument(currentDocument);
    }

    public String removeTemplateInput(String name) throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {

            Iterator<TemplateInput> it = templateEditableInputs.listIterator();
            while (it.hasNext()) {
                TemplateInput input = it.next();
                if (input.getName().equals(name)) {
                    it.remove();
                    break;
                }
            }

            currentDocument = template.saveParams(templateEditableInputs, true);
            newInput = null;
            templateEditableInputs = null;
            return navigationContext.navigateToDocument(currentDocument);
        } else {
            return null;
        }
    }

    public Collection<Type> getAllTypes() {
        return typeManager.getTypes();
    }

    public Collection<Type> getForcableTypes() {

        Collection<Type> types = typeManager.getTypes();

        Iterator<Type> it = types.iterator();
        while (it.hasNext()) {
            Type type = it.next();
            if (type.getId().equals("TemplateBasedFile")) {
                it.remove();
                break;
            }
        }
        return types;
    }

    public Collection<TemplateProcessorDescriptor> getRegistredTemplateProcessors() {
        return Framework.getLocalService(TemplateProcessorService.class).getRegisteredTemplateProcessors();
    }

    public List<String> getTemplateAndVersionsUUIDs() throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {
            List<String> uuids = new ArrayList<String>();
            uuids.add(currentDocument.getId());

            for (DocumentModel version : documentManager.getVersions(currentDocument.getRef())) {
                uuids.add(version.getId());
            }
            return uuids;
        }

        return new ArrayList<String>();
    }

    protected boolean showUsageListing = false;

    public boolean isShowUsageListing() {
        return showUsageListing;
    }

    public void setShowUsageListing(boolean showUsageListing) {
        this.showUsageListing = showUsageListing;
    }

}
