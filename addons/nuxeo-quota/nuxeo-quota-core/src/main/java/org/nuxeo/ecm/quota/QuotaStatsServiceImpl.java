/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 *     Florent Guillaume
 */
package org.nuxeo.ecm.quota;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkManager.Scheduling;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.quota.size.QuotaAware;
import org.nuxeo.ecm.quota.size.QuotaAwareDocumentFactory;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Default implementation of {@link org.nuxeo.ecm.quota.QuotaStatsService}.
 *
 * @since 5.5
 */
public class QuotaStatsServiceImpl extends DefaultComponent implements
        QuotaStatsService {

    private static final Log log = LogFactory.getLog(QuotaStatsServiceImpl.class);

    public static final String STATUS_INITIAL_COMPUTATION_QUEUED = "status.quota.initialComputationQueued";

    public static final String STATUS_INITIAL_COMPUTATION_PENDING = "status.quota.initialComputationInProgress";

    // TODO configurable through an ep?
    public static final int DEFAULT_BATCH_SIZE = 1000;

    public static final String QUOTA_STATS_UPDATERS_EP = "quotaStatsUpdaters";

    protected QuotaStatsUpdaterRegistry quotaStatsUpdaterRegistry;

    @Override
    public void activate(ComponentContext context) throws Exception {
        quotaStatsUpdaterRegistry = new QuotaStatsUpdaterRegistry();
    }

    @Override
    public List<QuotaStatsUpdater> getQuotaStatsUpdaters() {
        return quotaStatsUpdaterRegistry.getQuotaStatsUpdaters();
    }

    public QuotaStatsUpdater getQuotaStatsUpdaters(String updaterName) {
        return quotaStatsUpdaterRegistry.getQuotaStatsUpdater(updaterName);
    }

    @Override
    public void updateStatistics(final DocumentEventContext docCtx,
            final Event event) throws ClientException {
        // Open via session rather than repo name so that session.save and sync
        // is done automatically
        new UnrestrictedSessionRunner(docCtx.getCoreSession()) {
            @Override
            public void run() throws ClientException {
                List<QuotaStatsUpdater> quotaStatsUpdaters = quotaStatsUpdaterRegistry.getQuotaStatsUpdaters();
                for (QuotaStatsUpdater updater : quotaStatsUpdaters) {
                    log.debug("Calling updateStatistics on "
                            + updater.getName());
                    updater.updateStatistics(session, docCtx, event);
                }
            }
        }.runUnrestricted();
    }

    @Override
    public void computeInitialStatistics(String updaterName,
            CoreSession session, QuotaStatsInitialWork currentWorker) {
        QuotaStatsUpdater updater = quotaStatsUpdaterRegistry.getQuotaStatsUpdater(updaterName);
        if (updater != null) {
            updater.computeInitialStatistics(session, currentWorker);
        }
    }

    @Override
    public void launchInitialStatisticsComputation(String updaterName,
            String repositoryName) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        if (workManager == null) {
            throw new RuntimeException("No WorkManager available");
        }
        Work work = new QuotaStatsInitialWork(updaterName, repositoryName);
        workManager.schedule(work, Scheduling.IF_NOT_RUNNING_OR_SCHEDULED);
    }

    @Override
    public String getProgressStatus(String updaterName) {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        Work work = new QuotaStatsInitialWork(updaterName, null);
        work = workManager.find(work, null, true, null);
        if (work == null) {
            return null;
        } else if (work.getState() == State.SCHEDULED) {
            return STATUS_INITIAL_COMPUTATION_QUEUED;
        } else { // RUNNING
            return STATUS_INITIAL_COMPUTATION_PENDING;
        }
    }

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (QUOTA_STATS_UPDATERS_EP.equals(extensionPoint)) {
            quotaStatsUpdaterRegistry.addContribution((QuotaStatsUpdaterDescriptor) contribution);
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (QUOTA_STATS_UPDATERS_EP.equals(extensionPoint)) {
            quotaStatsUpdaterRegistry.removeContribution((QuotaStatsUpdaterDescriptor) contribution);
        }
    }

    @Override
    public long getQuotaFromParent(DocumentModel doc, CoreSession session)
            throws ClientException {
        List<DocumentModel> parents = getParentsInReverseOrder(doc, session);
        for (DocumentModel documentModel : parents) {
            QuotaAware qa = documentModel.getAdapter(QuotaAware.class);
            if (qa == null) {
                continue;
            }
            if (qa.getMaxQuota() > 0) {
                return qa.getMaxQuota();
            }
        }
        return -1;
    }

    @Override
    public boolean canSetMaxQuota(long maxQuota, DocumentModel doc,
            CoreSession session) throws ClientException {
        QuotaAware qa = null;
        DocumentModel parent = null;
        if ("UserWorkspacesRoot".equals(doc.getType())) {
            return true;
        }
        List<DocumentModel> parents = getParentsInReverseOrder(doc, session);
        for (DocumentModel p : parents) {
            if ("UserWorkspacesRoot".equals(p.getType())) {
                // checks don't apply to personal user workspaces
                return true;
            }
            qa = p.getAdapter(QuotaAware.class);
            if (qa == null) {
                // if no quota set on the parent, any value is valid
                continue;
            }
            if (qa.getMaxQuota() > 0) {
                parent = p;
                break;
            }
        }
        if (qa == null || qa.getMaxQuota() == -1) {
            return true;
        }

        long maxAllowedOnChildrenToSetQuota = qa.getMaxQuota() - maxQuota;
        if (maxAllowedOnChildrenToSetQuota < 0) {
            return false;
        }
        return canSetMaxQuotaOnChildrenTree(parent,
                maxAllowedOnChildrenToSetQuota, session);
    }

    @Override
    public void activateQuotaOnUserWorkspaces(final long maxQuota,
            CoreSession session) throws ClientException {
        final String userWorkspacesRootId = getUserWorkspaceRootId(
                session.getRootDocument(), session);
        new UnrestrictedSessionRunner(session) {
            @Override
            public void run() throws ClientException {
                DocumentModel uwRoot = session.getDocument(new IdRef(
                        userWorkspacesRootId));
                QuotaAware qa = uwRoot.getAdapter(QuotaAware.class);
                if (qa == null) {
                    qa = QuotaAwareDocumentFactory.make(uwRoot, false);
                }
                qa.setMaxQuota(maxQuota, true, false);

            };
        }.runUnrestricted();
    }

    @Override
    public long getQuotaSetOnUserWorkspaces(CoreSession session)
            throws ClientException {
        final String userWorkspacesRootId = getUserWorkspaceRootId(
                session.getRootDocument(), session);
        return new UnrestrictedSessionRunner(session) {

            long quota = -1;

            public long getsQuotaSetOnUserWorkspaces() throws ClientException {
                runUnrestricted();
                return quota;
            }

            @Override
            public void run() throws ClientException {
                DocumentModel uwRoot = session.getDocument(new IdRef(
                        userWorkspacesRootId));
                QuotaAware qa = uwRoot.getAdapter(QuotaAware.class);
                if (qa == null) {
                    quota = -1;
                } else {
                    quota = qa.getMaxQuota();
                }
            }
        }.getsQuotaSetOnUserWorkspaces();
    }

    protected List<DocumentModel> getParentsInReverseOrder(DocumentModel doc,
            CoreSession session) throws ClientException {
        UnrestrictedParentsFetcher parentsFetcher = new UnrestrictedParentsFetcher(
                doc, session);
        return parentsFetcher.getParents();
    }

    protected boolean canSetMaxQuotaOnChildrenTree(DocumentModel parent,
            Long maxAllowedOnChildrenToSetQuota, CoreSession session)
            throws ClientException {
        return new UnrestrictedQuotaOnChildrenCalculator(parent,
                maxAllowedOnChildrenToSetQuota, session).canSetQuota();
    }

    @Override
    public void launchSetMaxQuotaOnUserWorkspaces(final long maxSize,
            DocumentModel context, CoreSession session) throws ClientException {
        final String userWorkspacesId = getUserWorkspaceRootId(context, session);
        new UnrestrictedSessionRunner(session) {

            @Override
            public void run() throws ClientException {
                IterableQueryResult results = session.queryAndFetch(
                        String.format(
                                "Select ecm:uuid from Workspace where ecm:parentId = '%s'  "
                                        + "AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted' ",
                                userWorkspacesId), "NXQL");
                int size = 0;
                List<String> allIds = new ArrayList<String>();
                for (Map<String, Serializable> map : results) {
                    allIds.add((String) map.get("ecm:uuid"));
                }
                results.close();
                List<String> ids = new ArrayList<String>();
                WorkManager workManager = Framework.getLocalService(WorkManager.class);
                for (String id : allIds) {
                    ids.add(id);
                    size++;
                    if (size % DEFAULT_BATCH_SIZE == 0) {
                        QuotaMaxSizeSetterWork work = new QuotaMaxSizeSetterWork(
                                maxSize, ids, session.getRepositoryName());
                        workManager.schedule(work);
                        ids.clear();
                    }
                }
                if (ids.size() > 0) {
                    QuotaMaxSizeSetterWork work = new QuotaMaxSizeSetterWork(
                            maxSize, ids, session.getRepositoryName());
                    workManager.schedule(work);
                }
            }
        }.runUnrestricted();
    }

    public String getUserWorkspaceRootId(DocumentModel context,
            CoreSession session) throws ClientException {
        // get only the userworkspaces root under the first domain
        // it should be only one
        DocumentModel currentUserWorkspace = Framework.getLocalService(
                UserWorkspaceService.class).getUserPersonalWorkspace(
                (NuxeoPrincipal) session.getPrincipal(), context);
        return ((IdRef) currentUserWorkspace.getParentRef()).value;
    }

    class UnrestrictedQuotaOnChildrenCalculator extends
            UnrestrictedSessionRunner {

        DocumentModel parent;

        Long maxAllowedOnChildrenToSetQuota;

        boolean canSetQuota = true;

        protected UnrestrictedQuotaOnChildrenCalculator(DocumentModel parent,
                Long maxAllowedOnChildrenToSetQuota, CoreSession session) {
            super(session);
            this.parent = parent;
            this.maxAllowedOnChildrenToSetQuota = maxAllowedOnChildrenToSetQuota;
        }

        @Override
        public void run() throws ClientException {
            IterableQueryResult results = null;
            String userWorkspaceRootId = null;
            String queryString = "Select dss:maxSize from Document where ecm:path STARTSWITH '%s' AND ecm:mixinType = 'DocumentsSizeStatistics' AND ecm:primaryType NOT IN ('UserWorkspacesRoot') "
                    + " AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted' AND ecm:parentId !='%s' ORDER BY dss:maxSize DESC";
            try {
                userWorkspaceRootId = getUserWorkspaceRootId(
                        session.getRootDocument(), session);
            } catch (ClientException e) {
                // no userworkspaces root?
                log.error(e);
                queryString = "Select dss:maxSize from Document where ecm:path STARTSWITH '%s' AND ecm:mixinType = 'DocumentsSizeStatistics' AND ecm:primaryType NOT IN ('UserWorkspacesRoot') "
                        + " AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted' ORDER BY dss:maxSize DESC";
            }
            if (userWorkspaceRootId != null) {
                queryString = String.format(queryString,
                        parent.getPathAsString(), userWorkspaceRootId);

            } else {
                queryString = String.format(queryString,
                        parent.getPathAsString());
            }
            try {
                results = session.queryAndFetch(queryString, "NXQL");
                long quotaOnChildren = 0;
                for (Map<String, Serializable> result : results) {
                    Long maxSize = (Long) result.get("dss:maxSize");
                    quotaOnChildren = quotaOnChildren
                            + (maxSize == null || maxSize == -1 ? 0 : maxSize);
                    if (quotaOnChildren > maxAllowedOnChildrenToSetQuota) {
                        results.close();
                        canSetQuota = false;
                        return;
                    }
                }
            } catch (Exception e) {
                log.error(e);
                throw new ClientException(e);
            } finally {
                if (results != null) {
                    results.close();
                }
            }
        }

        public boolean canSetQuota() throws ClientException {
            runUnrestricted();
            return canSetQuota;
        }
    }

    class UnrestrictedParentsFetcher extends UnrestrictedSessionRunner {

        DocumentModel doc;

        List<DocumentModel> parents;

        protected UnrestrictedParentsFetcher(DocumentModel doc,
                CoreSession session) {
            super(session);
            this.doc = doc;
        }

        @Override
        public void run() throws ClientException {
            parents = new ArrayList<DocumentModel>();
            DocumentRef[] parentRefs = session.getParentDocumentRefs(doc.getRef());
            for (DocumentRef documentRef : parentRefs) {
                parents.add(session.getDocument(documentRef));
            }
            for (DocumentModel parent : parents) {
                parent.detach(true);
            }
        }

        public List<DocumentModel> getParents() throws ClientException {
            runUnrestricted();
            return parents;
        }
    }
}