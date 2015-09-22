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
 *     <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */

package org.nuxeo.ecm.quota.size;

import static org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener.DISABLE_DUBLINCORE_LISTENER;
import static org.nuxeo.ecm.platform.ec.notification.NotificationConstants.DISABLE_NOTIFICATION_SERVICE;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.collections.ScopeType;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.DeltaLong;
import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.ecm.quota.QuotaStatsService;
import org.nuxeo.ecm.quota.QuotaUtils;
import org.nuxeo.runtime.api.Framework;

/**
 * Adapter to manage a DocumentModel that supports Quotas
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 * @since 5.6
 */
public class QuotaAwareDocument implements QuotaAware {

    public static final String DOCUMENTS_SIZE_STATISTICS_FACET = "DocumentsSizeStatistics";

    public static final String DOCUMENTS_SIZE_INNER_SIZE_PROPERTY = "dss:innerSize";

    public static final String DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY = "dss:totalSize";

    public static final String DOCUMENTS_SIZE_TRASH_SIZE_PROPERTY = "dss:sizeTrash";

    public static final String DOCUMENTS_SIZE_VERSIONS_SIZE_PROPERTY = "dss:sizeVersions";

    public static final String DOCUMENTS_SIZE_MAX_SIZE_PROPERTY = "dss:maxSize";

    protected DocumentModel doc;

    protected static final Log log = LogFactory.getLog(QuotaAwareDocument.class);

    public QuotaAwareDocument(DocumentModel doc) {
        this.doc = doc;
    }

    @Override
    public DocumentModel getDoc() {
        return doc;
    }

    @Override
    public long getInnerSize() {
        try {
            Long inner = (Long) doc.getPropertyValue(DOCUMENTS_SIZE_INNER_SIZE_PROPERTY);
            return inner != null ? inner : 0;
        } catch (ClientException e) {
            return 0;
        }
    }

    @Override
    public long getTotalSize() {
        try {
            Long total = (Long) doc.getPropertyValue(DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY);
            return total != null ? total : 0;
        } catch (ClientException e) {
            return 0;
        }
    }

    @Override
    public long getTrashSize() {
        try {
            Long total = (Long) doc.getPropertyValue(DOCUMENTS_SIZE_TRASH_SIZE_PROPERTY);
            return total != null ? total : 0;
        } catch (ClientException e) {
            return 0;
        }
    }

    @Override
    public long getVersionsSize() {
        try {
            Long total = (Long) doc.getPropertyValue(DOCUMENTS_SIZE_VERSIONS_SIZE_PROPERTY);
            return total != null ? total : 0;
        } catch (ClientException e) {
            return 0;
        }
    }

    @Override
    public void setInnerSize(long size, boolean save) throws ClientException {
        doc.setPropertyValue(DOCUMENTS_SIZE_INNER_SIZE_PROPERTY, size);
        doc.setPropertyValue(DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY, size);
        if (log.isDebugEnabled()) {
            log.debug("Setting quota (inner size) : " + size + " on document " + doc.getId());
        }
        if (save) {
            save(true);
        }
    }

    protected Number addDelta(String property, long delta) {
        Number oldValue = (Number) doc.getPropertyValue(property);
        Number newValue = DeltaLong.deltaOrLong(oldValue, delta);
        if (newValue.longValue() < 0L) {
            newValue = 0L;
        }
        doc.setPropertyValue(property, newValue.longValue());
        return newValue;
    }

    @Override
    public void addInnerSize(long additionalSize, boolean save) throws ClientException {
        Number inner = addDelta(DOCUMENTS_SIZE_INNER_SIZE_PROPERTY, additionalSize);
        Number total = addDelta(DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY, additionalSize);
        if (log.isDebugEnabled()) {
            log.debug("Setting quota (inner size) : " + inner + ", (total size) : " + total + " on document "
                    + doc.getId());
        }
        if (save) {
            save(true);
        }
    }

    @Override
    public void addTotalSize(long additionalSize, boolean save) throws ClientException {
        Number total = addDelta(DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY, additionalSize);
        if (log.isDebugEnabled()) {
            log.debug("Setting quota (total size) : " + total + " on document " + doc.getId());
        }
        if (save) {
            save(true);
        }
    }

    @Override
    public void addTrashSize(long additionalSize, boolean save) throws ClientException {
        Number trash = addDelta(DOCUMENTS_SIZE_TRASH_SIZE_PROPERTY, additionalSize);
        if (log.isDebugEnabled()) {
            log.debug("Setting quota (trash size):" + trash + " on document " + doc.getId());
        }
        if (save) {
            save(true);
        }
    }

    @Override
    public void addVersionsSize(long additionalSize, boolean save) throws ClientException {
        Number versions = addDelta(DOCUMENTS_SIZE_VERSIONS_SIZE_PROPERTY, additionalSize);
        if (log.isDebugEnabled()) {
            log.debug("Setting quota (versions size): " + versions + " on document " + doc.getId());
        }
        if (save) {
            save(true);
        }
    }

    @Override
    public void save() {
        doc.putContextData(QuotaSyncListenerChecker.DISABLE_QUOTA_CHECK_LISTENER, Boolean.TRUE);
        QuotaUtils.disableListeners(doc);
        DocumentModel origDoc = doc;
        doc = doc.getCoreSession().saveDocument(doc);
        QuotaUtils.clearContextData(doc);
        QuotaUtils.clearContextData(origDoc);
    }

    @Override
    public void save(boolean disableNotifications) {
        // disableNotifications ignored
        save();
    }

    @Override
    public long getMaxQuota() {
        try {
            Long count = (Long) doc.getPropertyValue(DOCUMENTS_SIZE_MAX_SIZE_PROPERTY);
            return count != null ? count : -1;
        } catch (ClientException e) {
            return -1;
        }
    }

    @Override
    public void setMaxQuota(long maxSize, boolean save, boolean skipValidation) throws ClientException {
        if (!skipValidation) {
            if (!(Framework.getLocalService(QuotaStatsService.class).canSetMaxQuota(maxSize, doc, doc.getCoreSession()))) {
                throw new QuotaExceededException(doc, "Can not set " + maxSize
                        + ". Quota exceeded because the quota set on one of the children.");
            }
        }
        doc.setPropertyValue(DOCUMENTS_SIZE_MAX_SIZE_PROPERTY, maxSize);
        if (save) {
            save(false);
        }
    }

    @Override
    public void setMaxQuota(long maxSize, boolean save) throws ClientException {
        setMaxQuota(maxSize, save, false);
    }

    @Override
    public QuotaInfo getQuotaInfo() {
        return new QuotaInfo(getInnerSize(), getTotalSize(), getTrashSize(), getVersionsSize(), getMaxQuota());
    }

    /**
     * @throws ClientException
     * @since 5.7
     */
    @Override
    public void resetInfos(boolean save) throws ClientException {
        doc.setPropertyValue(DOCUMENTS_SIZE_INNER_SIZE_PROPERTY, 0L);
        doc.setPropertyValue(DOCUMENTS_SIZE_TOTAL_SIZE_PROPERTY, 0L);
        doc.setPropertyValue(DOCUMENTS_SIZE_MAX_SIZE_PROPERTY, 0L);
        doc.setPropertyValue(DOCUMENTS_SIZE_TRASH_SIZE_PROPERTY, 0L);
        doc.setPropertyValue(DOCUMENTS_SIZE_VERSIONS_SIZE_PROPERTY, 0L);
        try {
            invalidateTotalSizeCache();
        } catch (IOException e) {
            log.warn("Unable to invalidate cache");
        }
        if (save) {
            save(true);
        }
    }

    @Override
    public void invalidateTotalSizeCache() throws IOException {
        invalidateCache(QUOTA_TOTALSIZE_CACHE_NAME);
    }

    protected Cache getCache(String cacheName) {
        CacheService cs = Framework.getService(CacheService.class);
        Cache cache = cs.getCache(cacheName);
        if (cache != null) {
            log.trace("Using cache " + cacheName);
            return cache;
        } else {
            throw new RuntimeException("Unable to retrieve cache " + cacheName);
        }
    }

    protected void invalidateCache(String cacheName) throws IOException {
        try {
            Cache cache = getCache(cacheName);
            cache.invalidate(getCacheEntry(doc.getId()));
        } catch (RuntimeException e) {
            log.warn(e.getMessage());
        }

    }

    @Override
    public Long getTotalSizeCache() throws IOException {
        return getSizeInCache(QUOTA_TOTALSIZE_CACHE_NAME);
    }

    protected Long getSizeInCache(String cacheName) throws IOException {
        try {
            Cache cache = getCache(cacheName);
            return (Long) cache.get(getCacheEntry(doc.getId()));
        } catch (RuntimeException e) {
            log.warn(e.getMessage());
        }
        return null;
    }

    @Override
    public void putTotalSizeCache(long size) throws IOException {
        putSizeInCache(QUOTA_TOTALSIZE_CACHE_NAME, size);
    }

    protected void putSizeInCache(String cacheName, long size) throws IOException {
        try {
            Cache cache = getCache(cacheName);
            cache.put(getCacheEntry(doc.getId()), size);
        } catch (RuntimeException e) {
            log.warn(e.getMessage());
        }
    }

    @Override
    public boolean totalSizeCacheExists() {
        try {
            return (getCache(QUOTA_TOTALSIZE_CACHE_NAME) != null);
        } catch (RuntimeException e) {
            return false;
        }

    }

    protected String getCacheEntry(String... params) {
        return StringUtils.join(params, '-');
    }

}
