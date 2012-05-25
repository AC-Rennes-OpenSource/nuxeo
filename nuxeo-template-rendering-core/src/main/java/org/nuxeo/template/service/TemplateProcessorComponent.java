package org.nuxeo.template.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.template.adapters.doc.TemplateBasedDocumentAdapterImpl;
import org.nuxeo.template.adapters.doc.TemplateBinding;
import org.nuxeo.template.adapters.doc.TemplateBindings;
import org.nuxeo.template.api.TemplateProcessor;
import org.nuxeo.template.api.TemplateProcessorService;
import org.nuxeo.template.api.adapters.TemplateBasedDocument;
import org.nuxeo.template.api.adapters.TemplateSourceDocument;
import org.nuxeo.template.api.context.ContextExtensionFactory;
import org.nuxeo.template.api.context.DocumentWrapper;
import org.nuxeo.template.api.descriptor.ContextExtensionFactoryDescriptor;
import org.nuxeo.template.api.descriptor.TemplateProcessorDescriptor;

/**
 * Runtime Component used to handle Extension Points and expose the
 * {@link TemplateProcessorService} interface
 * 
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 * 
 */
public class TemplateProcessorComponent extends DefaultComponent implements
        TemplateProcessorService {

    protected static final Log log = LogFactory.getLog(TemplateProcessorComponent.class);

    public static final String PROCESSOR_XP = "processor";

    public static final String CONTEXT_EXTENSION_XP = "contextExtension";

    protected ContextFactoryRegistry contextExtensionRegistry;

    protected TemplateProcessorRegistry processorRegistry;

    protected ConcurrentHashMap<String, String> type2Template = null;

    @Override
    public void activate(ComponentContext context) throws Exception {
        processorRegistry = new TemplateProcessorRegistry();
        contextExtensionRegistry = new ContextFactoryRegistry();
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        processorRegistry = null;
        contextExtensionRegistry = null;
    }

    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (PROCESSOR_XP.equals(extensionPoint)) {
            processorRegistry.addContribution((TemplateProcessorDescriptor) contribution);
        } else if (CONTEXT_EXTENSION_XP.equals(extensionPoint)) {
            contextExtensionRegistry.addContribution((ContextExtensionFactoryDescriptor) contribution);
        }
    }

    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (PROCESSOR_XP.equals(extensionPoint)) {
            processorRegistry.removeContribution((TemplateProcessorDescriptor) contribution);
        } else if (CONTEXT_EXTENSION_XP.equals(extensionPoint)) {
            contextExtensionRegistry.removeContribution((ContextExtensionFactoryDescriptor) contribution);
        }
    }

    @Override
    public TemplateProcessor findProcessor(Blob templateBlob) {
        TemplateProcessorDescriptor desc = findProcessorDescriptor(templateBlob);
        if (desc != null) {
            return desc.getProcessor();
        } else {
            return null;
        }
    }

    @Override
    public String findProcessorName(Blob templateBlob) {
        TemplateProcessorDescriptor desc = findProcessorDescriptor(templateBlob);
        if (desc != null) {
            return desc.getName();
        } else {
            return null;
        }
    }

    public TemplateProcessorDescriptor findProcessorDescriptor(Blob templateBlob) {
        TemplateProcessorDescriptor processor = null;
        String mt = templateBlob.getMimeType();
        if (mt != null) {
            processor = findProcessorByMimeType(mt);
        }
        if (processor == null) {
            String fileName = templateBlob.getFilename();
            if (fileName != null) {
                String ext = FileUtils.getFileExtension(fileName);
                processor = findProcessorByExtension(ext);
            }
        }
        return processor;
    }

    public void addContextExtensions(DocumentModel currentDocument,
            DocumentWrapper wrapper, Map<String, Object> ctx) {

        Map<String, ContextExtensionFactoryDescriptor> factories = contextExtensionRegistry.getExtensionFactories();
        for (String name : factories.keySet()) {
            ContextExtensionFactory factory = factories.get(name).getExtensionFactory();
            if (factory != null) {
                Object ob = factory.getExtension(currentDocument, wrapper, ctx);
                if (ob != null) {
                    ctx.put(name, ob);
                    // also manage aliases
                    for (String alias : factories.get(name).getAliases()) {
                        ctx.put(alias, ob);
                    }
                }
            }
        }
    }

    public Map<String, ContextExtensionFactoryDescriptor> getRegistredContextExtensions() {
        return contextExtensionRegistry.getExtensionFactories();
    }

    protected TemplateProcessorDescriptor findProcessorByMimeType(String mt) {

        List<TemplateProcessorDescriptor> candidates = new ArrayList<TemplateProcessorDescriptor>();
        for (TemplateProcessorDescriptor desc : processorRegistry.getRegistredProcessors()) {
            if (desc.getSupportedMimeTypes().contains(mt)) {
                if (desc.isDefaultProcessor()) {
                    return desc;
                } else {
                    candidates.add(desc);
                }
            }
        }
        if (candidates.size() > 0) {
            return candidates.get(0);
        }
        return null;
    }

    protected TemplateProcessorDescriptor findProcessorByExtension(
            String extension) {

        List<TemplateProcessorDescriptor> candidates = new ArrayList<TemplateProcessorDescriptor>();
        for (TemplateProcessorDescriptor desc : processorRegistry.getRegistredProcessors()) {
            if (desc.getSupportedExtensions().contains(extension)) {
                if (desc.isDefaultProcessor()) {
                    return desc;
                } else {
                    candidates.add(desc);
                }
            }
        }
        if (candidates.size() > 0) {
            return candidates.get(0);
        }
        return null;
    }

    public TemplateProcessorDescriptor getDescriptor(String name) {
        return processorRegistry.getProcessorByName(name);
    }

    @Override
    public TemplateProcessor getProcessor(String name) {
        if (name == null) {
            log.warn("Can not get a TemplateProcessor with null name !!!");
            return null;
        }
        TemplateProcessorDescriptor desc = processorRegistry.getProcessorByName(name);
        if (desc != null) {
            return desc.getProcessor();
        } else {
            log.warn("Can not get a TemplateProcessor with name " + name);
            return null;
        }
    }

    protected String buildTemplateSearchQuery(String targetType) {
        StringBuffer sb = new StringBuffer(
                "select * from Document where ecm:mixinType = 'Template' AND ecm:currentLifeCycleState != 'deleted'");
        if (targetType != null) {
            sb.append(" AND tmpl:applicableTypes IN ( 'all', '" + targetType
                    + "')");
        }
        return sb.toString();
    }

    public List<DocumentModel> getAvailableTemplateDocs(CoreSession session,
            String targetType) throws ClientException {

        String query = buildTemplateSearchQuery(targetType);

        return session.query(query);
    }

    protected <T> List<T> wrap(List<DocumentModel> docs, Class<T> adapter) {
        List<T> result = new ArrayList<T>();
        for (DocumentModel doc : docs) {
            T adapted = doc.getAdapter(adapter);
            if (adapted != null) {
                result.add(adapted);
            }
        }
        return result;
    }

    public List<TemplateSourceDocument> getAvailableOfficeTemplates(
            CoreSession session, String targetType) throws ClientException {

        String query = buildTemplateSearchQuery(targetType);
        query = query + " AND tmpl:useAsMainContent=1";
        List<DocumentModel> docs = session.query(query);
        return wrap(docs, TemplateSourceDocument.class);
    }

    public List<TemplateSourceDocument> getAvailableTemplates(
            CoreSession session, String targetType) throws ClientException {
        List<DocumentModel> filtredResult = getAvailableTemplateDocs(session,
                targetType);
        return wrap(filtredResult, TemplateSourceDocument.class);
    }

    @Override
    public List<TemplateBasedDocument> getLinkedTemplateBasedDocuments(
            DocumentModel source) throws ClientException {

        StringBuffer sb = new StringBuffer(
                "select * from Document where ecm:isCheckedInVersion = 0 AND ecm:isProxy = 0 AND ");
        sb.append(TemplateBindings.BINDING_PROP_NAME + "/*/"
                + TemplateBinding.TEMPLATE_ID_KEY);
        sb.append(" = '");
        sb.append(source.getId());
        sb.append("'");
        DocumentModelList docs = source.getCoreSession().query(sb.toString());

        List<TemplateBasedDocument> result = new ArrayList<TemplateBasedDocument>();
        for (DocumentModel doc : docs) {
            TemplateBasedDocument templateBasedDocument = doc.getAdapter(TemplateBasedDocument.class);
            if (templateBasedDocument != null) {
                result.add(templateBasedDocument);
            }
        }
        return result;
    }

    public Collection<TemplateProcessorDescriptor> getRegisteredTemplateProcessors() {
        return processorRegistry.getRegistredProcessors();
    }

    public Map<String, String> getTypeMapping() {
        if (type2Template == null) {
            synchronized (this) {
                if (type2Template == null) {
                    type2Template = new ConcurrentHashMap<String, String>();
                    TemplateMappingFetcher fetcher = new TemplateMappingFetcher();
                    try {
                        fetcher.runUnrestricted();
                    } catch (ClientException e) {
                        log.error("Unable to fetch templates 2 types mapping",
                                e);
                    }
                    type2Template.putAll(fetcher.getMapping());
                }
            }
        }
        return type2Template;
    }

    public synchronized void registerTypeMapping(DocumentModel doc)
            throws ClientException {
        TemplateSourceDocument tmpl = doc.getAdapter(TemplateSourceDocument.class);
        if (tmpl != null) {
            Map<String, String> mapping = getTypeMapping();
            // check existing mapping for this docId
            List<String> boundTypes = new ArrayList<String>();
            for (String type : mapping.keySet()) {
                if (doc.getId().equals(mapping.get(type))) {
                    boundTypes.add(type);
                }
            }
            // unbind previous mapping for this docId
            for (String type : boundTypes) {
                mapping.remove(type);
            }
            // rebind types (with override)
            for (String type : tmpl.getForcedTypes()) {
                String uidToClean = mapping.get(type);
                if (uidToClean != null) {
                    new TemplateMappingRemover(doc.getCoreSession(),
                            uidToClean, type).runUnrestricted();
                }
                mapping.put(type, doc.getId());
            }
        }
    }

    public DocumentModel makeTemplateBasedDocument(DocumentModel targetDoc,
            DocumentModel sourceTemplateDoc, boolean save)
            throws ClientException {
        targetDoc.addFacet(TemplateBasedDocumentAdapterImpl.TEMPLATEBASED_FACET);
        TemplateBasedDocument tmplBased = targetDoc.getAdapter(TemplateBasedDocument.class);
        // bind the template
        return tmplBased.setTemplate(sourceTemplateDoc, save);
    }

    public DocumentModel detachTemplateBasedDocument(DocumentModel targetDoc,
            String templateName, boolean save) throws ClientException {
        TemplateBasedDocument tbd = targetDoc.getAdapter(TemplateBasedDocument.class);
        if (tbd != null) {
            if (!tbd.getTemplateNames().contains(templateName)) {
                return targetDoc;
            }
            if (tbd.getTemplateNames().size() == 1) {
                // remove the whole facet since there is no more binding
                targetDoc.removeFacet(TemplateBasedDocumentAdapterImpl.TEMPLATEBASED_FACET);
                if (save) {
                    targetDoc = targetDoc.getCoreSession().saveDocument(
                            targetDoc);
                }
            } else {
                // only remove the binding
                targetDoc = tbd.removeTemplateBinding(templateName, true);
            }
        }
        return targetDoc;
    }

}
