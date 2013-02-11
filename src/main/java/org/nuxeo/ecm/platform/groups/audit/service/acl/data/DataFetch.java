package org.nuxeo.ecm.platform.groups.audit.service.acl.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.CoreQueryPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

public class DataFetch {
    private static Log log = LogFactory.getLog(DataFetch.class);

    public static int DEFAULT_PAGE_SIZE = 100;

    public static boolean ORDERBY_PATH = true;

    public DocumentModelList getAllChildren(CoreSession session,
            DocumentModel doc) throws ClientException, IOException {
        String request = getChildrenDocQuery(doc, ORDERBY_PATH);
        log.debug("start query: " + request);
        DocumentModelList res = session.query(request);
        log.debug("done query");

        return res;
    }

    public PageProvider<DocumentModel> getAllChildrenPaginated(
            CoreSession session, DocumentModel doc) throws ClientException {
        return getAllChildrenPaginated(session, doc, DEFAULT_PAGE_SIZE,
                ORDERBY_PATH);
    }

    public CoreQueryDocumentPageProvider getAllChildrenPaginated(
            CoreSession session, DocumentModel doc, long pageSize,
            boolean orderByPath) throws ClientException {
        String request = getChildrenDocQuery(doc, orderByPath);
        log.debug("will initialize a paginated query:" + request);
        PageProviderService pps = Framework.getLocalService(PageProviderService.class);
        CoreQueryPageProviderDescriptor desc = new CoreQueryPageProviderDescriptor();
        desc.setPattern(request);

        // page provider parameters & init
        Long targetPage = null;
        Long targetPageSize = pageSize;
        List<SortInfo> sortInfos = null;
        Object[] parameters = null;
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (Serializable) session);

        PageProvider<?> provider = pps.getPageProvider("", desc, sortInfos,
                targetPageSize, targetPage, props, parameters);
        // TODO: edit pps implementation to really set parameters!
        provider.setPageSize(pageSize);
        provider.setMaxPageSize(pageSize);
        CoreQueryDocumentPageProvider cqdpp = (CoreQueryDocumentPageProvider) provider;
        return cqdpp;
    }

    /* QUERIES */

    public String getChildrenDocQuery(DocumentModel doc, boolean ordered) {
        String parentPath = doc.getPathAsString();

        String request = "SELECT * FROM Document WHERE "
                + "ecm:path STARTSWITH '" + parentPath + "'" + " AND "
                + baseRequest();
        if (ordered)
            return request + " ORDER BY ecm:path";
        else
            return request;
    }

    /**
     * Exclude documents:
     * <ul>
     * <li>from user workspaces
     * <li>that are deleted (in trash)
     * <li>that stand in user workspace
     * </ul>
     *
     * @return
     */
    protected static String baseRequest() {
        return "ecm:mixinType != 'HiddenInNavigation'"
                + " AND ecm:isCheckedInVersion = 0"
                + " AND ecm:currentLifeCycleState != 'deleted'";
    }
}
