/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.drive.service.impl.AuditChangeFinder;
import org.nuxeo.drive.service.impl.RootDefinitionsHelper;
import org.nuxeo.ecm.collections.api.CollectionManager;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
import org.nuxeo.ecm.core.test.RepositorySettings;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Base class to test the {@link FileSystemChangeFinder} implementations.
 *
 * @since 7.3
 */
@RunWith(FeaturesRunner.class)
// We handle transaction start and commit manually to make it possible to have
// several consecutive transactions in a test method
@Deploy({ "org.nuxeo.ecm.platform.userworkspace.types", "org.nuxeo.ecm.platform.userworkspace.core",
        "org.nuxeo.drive.core", "org.nuxeo.ecm.platform.collections.core",
        "org.nuxeo.drive.core.test:OSGI-INF/test-nuxeodrive-types-contrib.xml" })
public abstract class AbstractChangeFinderTestCase {

    private static final Log log = LogFactory.getLog(AbstractChangeFinderTestCase.class);

    @Inject
    protected CoreSession session;

    @Inject
    protected RepositorySettings repository;

    @Inject
    protected DirectoryService directoryService;

    @Inject
    protected NuxeoDriveManager nuxeoDriveManager;

    @Inject
    protected EventServiceAdmin eventServiceAdmin;

    @Inject
    protected WorkManager workManager;

    @Inject
    protected CollectionManager collectionManager;

    protected long lastEventLogId;

    protected String lastSyncActiveRootDefinitions;

    protected DocumentModel folder1;

    protected DocumentModel folder2;

    protected DocumentModel folder3;

    protected CoreSession user1Session;

    @Before
    public void init() throws Exception {
        // Enable deletion listener because the tear down disables it
        eventServiceAdmin.setListenerEnabledFlag("nuxeoDriveFileSystemDeletionListener", true);

        lastEventLogId = 0;
        lastSyncActiveRootDefinitions = "";
        Framework.getProperties().put("org.nuxeo.drive.document.change.limit", "10");

        // Create test users
        try (Session userDir = directoryService.open("userDirectory")) {
            if (userDir.getEntry("user1") != null) {
                userDir.deleteEntry("user1");
            }
            Map<String, Object> user1 = new HashMap<String, Object>();
            user1.put("username", "user1");
            user1.put("groups", Arrays.asList(new String[] { "members" }));
            userDir.createEntry(user1);
        }
        user1Session = repository.openSessionAs("user1");

        commitAndWaitForAsyncCompletion();

        folder1 = session.createDocument(session.createDocumentModel("/", "folder1", "Folder"));
        folder2 = session.createDocument(session.createDocumentModel("/", "folder2", "Folder"));
        folder3 = session.createDocument(session.createDocumentModel("/", "folder3", "Folder"));
        setPermissions(folder1, new ACE("user1", SecurityConstants.READ_WRITE));
        setPermissions(folder2, new ACE("user1", SecurityConstants.READ_WRITE));

        commitAndWaitForAsyncCompletion();
    }

    @After
    public void tearDown() throws Exception {

        if (user1Session != null) {
            user1Session.close();
        }
        try (Session usersDir = directoryService.open("userDirectory")) {
            if (usersDir.getEntry("user1") != null) {
                usersDir.deleteEntry("user1");
            }
        }

        // Disable deletion listener for the repository cleanup phase done in
        // CoreFeature#afterTeardown to avoid exception due to no active
        // transaction in FileSystemItemManagerImpl#getSession
        eventServiceAdmin.setListenerEnabledFlag("nuxeoDriveFileSystemDeletionListener", false);

        // Clean up audit log
        cleanUpAuditLog();
    }

    @Test
    public void testFindChanges() throws Exception {
        List<FileSystemItemChange> changes;
        DocumentModel doc1;
        DocumentModel doc2;
        DocumentModel doc3;
        DocumentModel docToCopy;
        DocumentModel copiedDoc;
        DocumentModel docToVersion;

        commitAndWaitForAsyncCompletion();
        try {
            // No sync roots
            changes = getChanges();
            assertNotNull(changes);
            assertTrue(changes.isEmpty());

            log.trace("Sync roots for Administrator");
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder1, session);
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Get changes for Administrator
            changes = getChanges();
            // Root registration events
            assertEquals(2, changes.size());

            log.trace("Create 3 documents, only 2 in sync roots");
            doc1 = session.createDocumentModel("/folder1", "doc1", "File");
            doc1.setPropertyValue("file:content", new StringBlob("The content of file 1."));
            doc1 = session.createDocument(doc1);
            doc2 = session.createDocumentModel("/folder2", "doc2", "File");
            doc2.setPropertyValue("file:content", new StringBlob("The content of file 2."));
            doc2 = session.createDocument(doc2);
            doc3 = session.createDocumentModel("/folder3", "doc3", "File");
            doc3.setPropertyValue("file:content", new StringBlob("The content of file 3."));
            doc3 = session.createDocument(doc3);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(doc2.getId(), "documentCreated", "test"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "documentCreated", "test"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            // No changes since last successful sync
            changes = getChanges();
            assertTrue(changes.isEmpty());

            log.trace("Update both synchronized documents and unsynchronize a root");
            doc1.setPropertyValue("file:content", new StringBlob("The content of file 1, updated."));
            session.saveDocument(doc1);
            doc2.setPropertyValue("file:content", new StringBlob("The content of file 2, updated."));
            session.saveDocument(doc2);
            nuxeoDriveManager.unregisterSynchronizationRoot(session.getPrincipal(), folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(2, changes.size());
            // The root unregistration is mapped to a fake deletion from the
            // client's point of view
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "deleted", "test"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "documentModified", "test"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Delete a document with a lifecycle transition (trash)");
            session.followTransition(doc1.getRef(), "delete");
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(doc1.getId(), "deleted", "test", "test#" + doc1.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Restore a deleted document and move a document in a newly synchronized root");
            session.followTransition(doc1.getRef(), "undelete");
            session.move(doc3.getRef(), folder2.getRef(), null);
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(3, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder2.getId()));
            expectedChanges.add(new SimpleFileSystemItemChange(doc3.getId(), "documentMoved", "test"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "lifecycle_transition_event", "test"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Physical deletion without triggering the delete transition first");
            session.removeDocument(doc3.getRef());
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(doc3.getId(), "deleted", "test", "test#" + doc3.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Create a doc and copy it from a sync root to another one");
            docToCopy = session.createDocumentModel("/folder1", "docToCopy", "File");
            docToCopy.setPropertyValue("file:content", new StringBlob("The content of file to copy."));
            docToCopy = session.createDocument(docToCopy);
            copiedDoc = session.copy(docToCopy.getRef(), folder2.getRef(), null);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(copiedDoc.getId(), "documentCreatedByCopy", "test",
                    "defaultFileSystemItemFactory#test#" + copiedDoc.getId(), "docToCopy"));
            expectedChanges.add(new SimpleFileSystemItemChange(docToCopy.getId(), "documentCreated", "test",
                    "defaultFileSystemItemFactory#test#" + docToCopy.getId(), "docToCopy"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Remove file from a document, mapped to a fake deletion from the client's point of view");
            doc1.setPropertyValue("file:content", null);
            session.saveDocument(doc1);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(doc1.getId(), "deleted", "test"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Move a doc from a sync root to another sync root");
            session.move(copiedDoc.getRef(), folder1.getRef(), null);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(copiedDoc.getId(), "documentMoved", "test"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Move a doc from a sync root to a non synchronized folder");
            session.move(copiedDoc.getRef(), folder3.getRef(), null);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(copiedDoc.getId(), "deleted", "test"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Create a doc, create a version of it, update doc and restore the version");
            docToVersion = session.createDocumentModel("/folder1", "docToVersion", "File");
            docToVersion.setPropertyValue("file:content", new StringBlob("The content of file to version."));
            docToVersion = session.createDocument(docToVersion);
            docToVersion.putContextData(VersioningService.VERSIONING_OPTION, VersioningOption.MAJOR);
            session.saveDocument(docToVersion);
            docToVersion.setPropertyValue("file:content", new StringBlob("Updated content of the versioned file."));
            session.saveDocument(docToVersion);
            List<DocumentModel> versions = session.getVersions(docToVersion.getRef());
            assertEquals(1, versions.size());
            DocumentModel version = versions.get(0);
            session.restoreToVersion(docToVersion.getRef(), version.getRef());
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges();
            // Expecting 4 (among which 3 distinct) changes:
            // - documentRestored for docToVersion
            // - documentModified for docToVersion (2 occurrences)
            // - documentCreated for docToVersion
            assertEquals(4, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(docToVersion.getId(), "documentRestored"));
            expectedChanges.add(new SimpleFileSystemItemChange(docToVersion.getId(), "documentModified"));
            expectedChanges.add(new SimpleFileSystemItemChange(docToVersion.getId(), "documentCreated"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Too many changes");
            session.followTransition(doc1.getRef(), "delete");
            session.followTransition(doc2.getRef(), "delete");
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        Framework.getProperties().put("org.nuxeo.drive.document.change.limit", "1");
        FileSystemChangeSummary changeSummary = getChangeSummary(session.getPrincipal());
        assertEquals(true, changeSummary.getHasTooManyChanges());
    }

    @Test
    public void testFindSecurityChanges() throws Exception {
        List<FileSystemItemChange> changes;
        DocumentModel subFolder;

        try {
            // No sync roots
            changes = getChanges();
            assertTrue(changes.isEmpty());

            // Create a folder in a sync root
            subFolder = user1Session.createDocumentModel("/folder1", "subFolder", "Folder");
            subFolder = user1Session.createDocument(subFolder);

            // Sync roots for user1
            nuxeoDriveManager.registerSynchronizationRoot(user1Session.getPrincipal(), folder1, user1Session);
            nuxeoDriveManager.registerSynchronizationRoot(user1Session.getPrincipal(), folder2, user1Session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Get changes for user1
            changes = getChanges(user1Session.getPrincipal());
            // Folder creation and sync root registration events
            assertEquals(3, changes.size());

            // Permission changes: deny Read
            // Deny Read to user1 on a regular doc
            setPermissions(subFolder, new ACE(SecurityConstants.ADMINISTRATOR, SecurityConstants.EVERYTHING), ACE.BLOCK);
            // Deny Read to user1 on a sync root
            setPermissions(folder2, new ACE(SecurityConstants.ADMINISTRATOR, SecurityConstants.EVERYTHING), ACE.BLOCK);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changes = getChanges(user1Session.getPrincipal());
            assertEquals(2, changes.size());

            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "securityUpdated", "test", "test#"
                    + folder2.getId(), "folder2"));
            expectedChanges.add(new SimpleFileSystemItemChange(subFolder.getId(), "securityUpdated", "test", "test#"
                    + subFolder.getId(), "subFolder"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));
            // Changed documents are not adaptable as a FileSystemItem since no Read permission
            for (FileSystemItemChange change : changes) {
                assertNull(change.getFileSystemItem());
            }

            // Permission changes: grant Read
            // Grant Read to user1 on a regular doc
            setPermissions(subFolder, new ACE("user1", SecurityConstants.READ));
            // Grant Read to user1 on a sync root
            setPermissions(folder2, new ACE("user1", SecurityConstants.READ));
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        changes = getChanges(user1Session.getPrincipal());
        assertEquals(2, changes.size());
        Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
        expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "securityUpdated", "test",
                "defaultSyncRootFolderItemFactory#test#" + folder2.getId(), "folder2"));
        expectedChanges.add(new SimpleFileSystemItemChange(subFolder.getId(), "securityUpdated", "test",
                "defaultFileSystemItemFactory#test#" + subFolder.getId(), "subFolder"));
        assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));
        // Changed documents are adaptable as a FileSystemItem since Read permission
        for (FileSystemItemChange change : changes) {
            assertNotNull(change.getFileSystemItem());
        }
    }

    @Test
    public void testGetChangeSummary() throws Exception {
        FileSystemChangeSummary changeSummary;
        Principal admin = new NuxeoPrincipalImpl("Administrator");
        DocumentModel doc1;
        DocumentModel doc2;

        try {
            // No sync roots => shouldn't find any changes
            changeSummary = getChangeSummary(admin);
            assertNotNull(changeSummary);
            assertTrue(changeSummary.getFileSystemChanges().isEmpty());
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

            // Register sync roots => should find changes: the newly
            // synchronized root folders as they are updated by the
            // synchronization
            // registration process
            nuxeoDriveManager.registerSynchronizationRoot(admin, folder1, session);
            nuxeoDriveManager.registerSynchronizationRoot(admin, folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changeSummary = getChangeSummary(admin);
            assertEquals(2, changeSummary.getFileSystemChanges().size());
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        // Create 3 documents, only 2 in sync roots => should find 2 changes
        try {
            doc1 = session.createDocumentModel("/folder1", "doc1", "File");
            doc1.setPropertyValue("file:content", new StringBlob("The content of file 1."));
            doc1 = session.createDocument(doc1);
            doc2 = session.createDocumentModel("/folder2", "doc2", "File");
            doc2.setPropertyValue("file:content", new StringBlob("The content of file 2."));
            doc2 = session.createDocument(doc2);
            session.createDocument(session.createDocumentModel("/folder3", "doc3", "File"));
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changeSummary = getChangeSummary(admin);

            List<FileSystemItemChange> changes = changeSummary.getFileSystemChanges();
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            SimpleFileSystemItemChange simpleChange = new SimpleFileSystemItemChange(doc2.getId(), "documentCreated",
                    "test");
            simpleChange.setLifeCycleState("project");
            expectedChanges.add(simpleChange);
            simpleChange = new SimpleFileSystemItemChange(doc1.getId(), "documentCreated", "test");
            simpleChange.setLifeCycleState("project");
            expectedChanges.add(simpleChange);
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

            // Create a document that should not be synchronized because not
            // adaptable as a FileSystemItem (not Folderish nor a BlobHolder
            // with a
            // blob) => should not be considered as a change
            session.createDocument(session.createDocumentModel("/folder1", "notSynchronizableDoc", "NotSynchronizable"));
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changeSummary = getChangeSummary(admin);
            assertTrue(changeSummary.getFileSystemChanges().isEmpty());
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

            // Create 2 documents in the same sync root: "/folder1" and 1 document in another sync root => should find 2
            // changes for "/folder1"
            DocumentModel doc3 = session.createDocumentModel("/folder1", "doc3", "File");
            doc3.setPropertyValue("file:content", new StringBlob("The content of file 3."));
            doc3 = session.createDocument(doc3);
            DocumentModel doc4 = session.createDocumentModel("/folder1", "doc4", "File");
            doc4.setPropertyValue("file:content", new StringBlob("The content of file 4."));
            doc4 = session.createDocument(doc4);
            DocumentModel doc5 = session.createDocumentModel("/folder2", "doc5", "File");
            doc5.setPropertyValue("file:content", new StringBlob("The content of file 5."));
            doc5 = session.createDocument(doc5);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            changeSummary = getChangeSummary(admin);
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());
            assertEquals(3, changeSummary.getFileSystemChanges().size());

            // No changes since last successful sync
            changeSummary = getChangeSummary(admin);
            assertTrue(changeSummary.getFileSystemChanges().isEmpty());
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

            // Test too many changes
            session.followTransition(doc1.getRef(), "delete");
            session.followTransition(doc2.getRef(), "delete");
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        Framework.getProperties().put("org.nuxeo.drive.document.change.limit", "1");
        changeSummary = getChangeSummary(admin);
        assertTrue(changeSummary.getFileSystemChanges().isEmpty());
        assertEquals(Boolean.TRUE, changeSummary.getHasTooManyChanges());
    }

    @Test
    public void testGetChangeSummaryOnRootDocuments() throws Exception {
        Principal admin = new NuxeoPrincipalImpl("Administrator");
        Principal otherUser = new NuxeoPrincipalImpl("some-other-user");
        Set<IdRef> activeRootRefs;
        FileSystemChangeSummary changeSummary;
        List<FileSystemItemChange> changes;

        try {
            // No root registered by default: no changes
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());

            changeSummary = getChangeSummary(admin);
            assertNotNull(changeSummary);
            assertTrue(changeSummary.getFileSystemChanges().isEmpty());
            assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

            // Register a root for someone else
            nuxeoDriveManager.registerSynchronizationRoot(otherUser, folder1, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Administrator does not see any change
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());

            changeSummary = getChangeSummary(admin);
            assertNotNull(changeSummary);
            assertTrue(changeSummary.getFileSystemChanges().isEmpty());
            assertFalse(changeSummary.getHasTooManyChanges());

            // Register a new sync root
            nuxeoDriveManager.registerSynchronizationRoot(admin, folder1, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertEquals(1, activeRootRefs.size());
            assertEquals(folder1.getRef(), activeRootRefs.iterator().next());

            // The new sync root is detected in the change summary
            changeSummary = getChangeSummary(admin);
            assertNotNull(changeSummary);
            changes = changeSummary.getFileSystemChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder1.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));

            // Check that root unregistration is detected as a deletion
            nuxeoDriveManager.unregisterSynchronizationRoot(admin, folder1, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());
            changeSummary = getChangeSummary(admin);
            changes = changeSummary.getFileSystemChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(folder1.getId(), "deleted", "test", "test#" + folder1.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));

            // Register back the root, it's activity is again detected by the
            // client
            nuxeoDriveManager.registerSynchronizationRoot(admin, folder1, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertEquals(activeRootRefs.size(), 1);

            changeSummary = getChangeSummary(admin);
            changes = changeSummary.getFileSystemChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder1.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));

            // Test deletion of a root
            session.followTransition(folder1.getRef(), "delete");
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());

            // The root is no longer active
            activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());

            // The deletion of the root itself is mapped as filesystem
            // deletion event
            changeSummary = getChangeSummary(admin);
            changes = changeSummary.getFileSystemChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(folder1.getId(), "deleted", "test", "test#" + folder1.getId()),
                    toSimpleFileSystemItemChange(changes.get(0)));
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    @Test
    public void testSyncUnsyncRootsAsAnotherUser() throws Exception {
        Principal user1Principal = user1Session.getPrincipal();
        List<FileSystemItemChange> changes;

        try {
            // No sync roots expected for user1
            changes = getChanges(user1Principal);
            assertNotNull(changes);
            assertTrue(changes.isEmpty());

            // Register sync roots for user1 as Administrator
            nuxeoDriveManager.registerSynchronizationRoot(user1Principal, folder1, session);
            nuxeoDriveManager.registerSynchronizationRoot(user1Principal, folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // user1 should have 2 sync roots
            Set<IdRef> activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(user1Session);
            assertNotNull(activeRootRefs);
            assertEquals(2, activeRootRefs.size());
            assertTrue(activeRootRefs.contains(folder1.getRef()));
            assertTrue(activeRootRefs.contains(folder2.getRef()));

            // There should be 2 changes detected in the audit
            changes = getChanges(user1Principal);
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder2.getId(), "folder2"));
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder1.getId(), "folder1"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));
            for (FileSystemItemChange change : changes) {
                assertNotNull(change.getFileSystemItem());
            }

            // Unregister sync roots for user1 as Administrator
            nuxeoDriveManager.unregisterSynchronizationRoot(user1Principal, folder1, session);
            nuxeoDriveManager.unregisterSynchronizationRoot(user1Principal, folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // user1 should have no sync roots
            Set<IdRef> activeRootRefs = nuxeoDriveManager.getSynchronizationRootReferences(user1Session);
            assertNotNull(activeRootRefs);
            assertTrue(activeRootRefs.isEmpty());

            // There should be 2 changes detected in the audit
            changes = getChanges(user1Principal);
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder2.getId(), "deleted", "test", "test#"
                    + folder2.getId(), "folder2"));
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "deleted", "test", "test#"
                    + folder1.getId(), "folder1"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));
            // Not adaptable as a FileSystemItem since unregistered
            for (FileSystemItemChange change : changes) {
                assertNull(change.getFileSystemItem());
            }
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    @Test
    public void testRegisterSyncRootAndUpdate() throws Exception {

        try {
            // Register folder1 as a sync root for Administrator
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder1, session);

            // Update folder1 title
            folder1.setPropertyValue("dc:title", "folder1 updated");
            session.saveDocument(folder1);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes, expecting 3:
            // - documentModified
            // - rootRegistered
            // - documentCreated (at init)
            List<FileSystemItemChange> changes = getChanges();
            assertEquals(3, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "documentModified"));
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered"));
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "documentCreated"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            // Unregister folder1 as a sync root for Administrator
            nuxeoDriveManager.unregisterSynchronizationRoot(session.getPrincipal(), folder1, session);

            // Update folder1 title
            folder1.setPropertyValue("dc:title", "folder1 updated twice");
            session.saveDocument(folder1);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes, expecting 1: deleted
            List<FileSystemItemChange> changes = getChanges();
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(folder1.getId(), "deleted"),
                    toSimpleFileSystemItemChange(changes.get(0)));
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    @Test
    public void testMoveToOtherUsersSyncRoot() throws Exception {
        DocumentModel subFolder;
        List<FileSystemItemChange> changes;
        try {
            // Create a subfolder in folder1 as Administrator
            subFolder = session.createDocument(session.createDocumentModel(folder1.getPathAsString(), "subFolder",
                    "Folder"));
            // Register folder1 as a sync root for user1
            nuxeoDriveManager.registerSynchronizationRoot(user1Session.getPrincipal(), folder1, user1Session);
            // Register folder2 as a sync root for Administrator
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder2, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes for user1, expecting 3:
            // - rootRegistered for folder1
            // - documentCreated for subFolder1
            // - documentCreated for folder1 at init
            changes = getChanges(user1Session.getPrincipal());
            assertEquals(3, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered"));
            expectedChanges.add(new SimpleFileSystemItemChange(subFolder.getId(), "documentCreated"));
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "documentCreated"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            // As Administrator, move subfolder from folder1 (sync root for
            // user1) to folder2 (sync root for Administrator but not for
            // user1)
            session.move(subFolder.getRef(), folder2.getRef(), null);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes for user1, expecting 1: deleted for subFolder
            changes = getChanges(user1Session.getPrincipal());
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(subFolder.getId(), "deleted"),
                    toSimpleFileSystemItemChange(changes.get(0)));
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    @Test
    public void testCollectionEvents() throws Exception {
        DocumentModel doc1;
        DocumentModel doc2;
        DocumentModel doc3;
        List<FileSystemItemChange> changes;
        DocumentModel locallyEditedCollection;
        try {
            log.trace("Create 2 test docs and them to the 'Locally Edited' collection");
            doc1 = session.createDocumentModel(folder1.getPathAsString(), "doc1", "File");
            doc1.setPropertyValue("file:content", new StringBlob("File content."));
            doc1 = session.createDocument(doc1);
            doc2 = session.createDocumentModel(folder1.getPathAsString(), "doc2", "File");
            doc2.setPropertyValue("file:content", new StringBlob("File content."));
            doc2 = session.createDocument(doc2);
            nuxeoDriveManager.addToLocallyEditedCollection(session, doc1);
            nuxeoDriveManager.addToLocallyEditedCollection(session, doc2);
            DocumentModel userCollections = collectionManager.getUserDefaultCollections(folder1, session);
            DocumentRef locallyEditedCollectionRef = new PathRef(userCollections.getPath().toString(),
                    NuxeoDriveManager.LOCALLY_EDITED_COLLECTION_NAME);
            locallyEditedCollection = session.getDocument(locallyEditedCollectionRef);
            // Re-fetch documents to get rid of the disabled events in context
            // data
            doc1 = session.getDocument(doc1.getRef());
            doc2 = session.getDocument(doc2.getRef());
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 8 (among which 7 distinct) changes:
            // - addedToCollection for doc2
            // - documentModified for 'Locally Edited' collection (2 occurrences)
            // - rootRegistered for 'Locally Edited' collection
            // - addedToCollection for doc1
            // - documentCreated for 'Locally Edited' collection
            // - documentCreated for doc2
            // - documentCreated for doc1
            changes = getChanges(session.getPrincipal());
            assertEquals(8, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(doc2.getId(), "addedToCollection"));
            expectedChanges.add(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "documentModified"));
            expectedChanges.add(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "rootRegistered"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "addedToCollection"));
            expectedChanges.add(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "documentCreated"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc2.getId(), "documentCreated"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "documentCreated"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Update doc1 member of the 'Locally Edited' collection");
            doc1.setPropertyValue("file:content", new StringBlob("Updated file content."));
            session.saveDocument(doc1);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 1 change: documentModified for doc1
            changes = getChanges(session.getPrincipal());
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(doc1.getId(), "documentModified"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Remove doc1 from the 'Locally Edited' collection, delete doc2 and add doc 3 to the collection");
            collectionManager.removeFromCollection(locallyEditedCollection, doc1, session);
            doc2.followTransition(LifeCycleConstants.DELETE_TRANSITION);
            doc3 = session.createDocumentModel(folder1.getPathAsString(), "doc3", "File");
            doc3.setPropertyValue("file:content", new StringBlob("File content."));
            doc3 = session.createDocument(doc3);
            collectionManager.addToCollection(locallyEditedCollection, doc3, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 6 (among which 5 distinct) changes:
            // - addedToCollection for doc3
            // - documentModified for 'Locally Edited' collection (2 occurrences)
            // - documentCreated for doc3
            // - deleted for doc2
            // - deleted for doc1
            changes = getChanges(session.getPrincipal());
            assertEquals(6, changes.size());
            List<SimpleFileSystemItemChange> expectedChanges = new ArrayList<>();
            expectedChanges.add(new SimpleFileSystemItemChange(doc3.getId(), "addedToCollection"));
            expectedChanges.add(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "documentModified"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc3.getId(), "documentCreated"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc2.getId(), "deleted"));
            expectedChanges.add(new SimpleFileSystemItemChange(doc1.getId(), "deleted"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));

            log.trace("Unregister the 'Locally Edited' collection as a sync root");
            nuxeoDriveManager.unregisterSynchronizationRoot(session.getPrincipal(), locallyEditedCollection, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 1 change: deleted for 'Locally Edited' collection
            changes = getChanges(session.getPrincipal());
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "deleted"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Register the 'Locally Edited' collection back as a sync root");
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), locallyEditedCollection, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 1 change: rootRegistered for 'Locally Edited'
            // collection
            changes = getChanges(session.getPrincipal());
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "rootRegistered"),
                    toSimpleFileSystemItemChange(changes.get(0)));

            log.trace("Delete the 'Locally Edited' collection");
            locallyEditedCollection.followTransition(LifeCycleConstants.DELETE_TRANSITION);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Expecting 1 change: deleted for 'Locally Edited' collection
            changes = getChanges(session.getPrincipal());
            assertEquals(1, changes.size());
            assertEquals(new SimpleFileSystemItemChange(locallyEditedCollection.getId(), "deleted"),
                    toSimpleFileSystemItemChange(changes.get(0)));
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    @Test
    public void testRegisterParentSyncRoot() throws Exception {
        DocumentModel subFolder;
        List<FileSystemItemChange> changes;
        try {
            // Create a subfolder in folder1
            subFolder = session.createDocument(session.createDocumentModel(folder1.getPathAsString(), "subFolder",
                    "Folder"));
            // Register subfolder as a sync root
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), subFolder, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes, expecting 2:
            // - rootRegistered for subfolder
            // - documentCreated for subFolder
            changes = getChanges(session.getPrincipal());
            assertEquals(2, changes.size());

            // Register folder1 as a sync root
            nuxeoDriveManager.registerSynchronizationRoot(session.getPrincipal(), folder1, session);
        } finally {
            commitAndWaitForAsyncCompletion();
        }

        try {
            // Check changes, expecting 2:
            // - rootRegistered for folder1
            // - deleted for subFolder
            changes = getChanges(session.getPrincipal());
            assertEquals(2, changes.size());
            Set<SimpleFileSystemItemChange> expectedChanges = new HashSet<SimpleFileSystemItemChange>();
            expectedChanges.add(new SimpleFileSystemItemChange(folder1.getId(), "rootRegistered", "test",
                    "defaultSyncRootFolderItemFactory#test#" + folder1.getId(), "folder1"));
            expectedChanges.add(new SimpleFileSystemItemChange(subFolder.getId(), "deleted", "test", "test#"
                    + subFolder.getId(), "subFolder"));
            assertTrue(CollectionUtils.isEqualCollection(expectedChanges, toSimpleFileSystemItemChanges(changes)));
        } finally {
            commitAndWaitForAsyncCompletion();
        }
    }

    /**
     * Gets the document changes for the given user's synchronization roots using the {@link AuditChangeFinder} and
     * updates {@link #lastEventLogId}.
     */
    protected List<FileSystemItemChange> getChanges(Principal principal) throws InterruptedException {
        return getChangeSummary(principal).getFileSystemChanges();
    }

    /**
     * Gets the document changes for the Administrator user.
     */
    protected List<FileSystemItemChange> getChanges() throws InterruptedException {
        return getChanges(session.getPrincipal());
    }

    /**
     * Gets the document changes summary for the given user's synchronization roots using the {@link NuxeoDriveManager}
     * and updates {@link #lastEventLogId}.
     */
    protected FileSystemChangeSummary getChangeSummary(Principal principal) throws InterruptedException {
        Map<String, Set<IdRef>> lastSyncActiveRootRefs = RootDefinitionsHelper.parseRootDefinitions(lastSyncActiveRootDefinitions);
        FileSystemChangeSummary changeSummary = nuxeoDriveManager.getChangeSummaryIntegerBounds(principal,
                lastSyncActiveRootRefs, lastEventLogId);
        assertNotNull(changeSummary);
        lastEventLogId = changeSummary.getUpperBound();
        lastSyncActiveRootDefinitions = changeSummary.getActiveSynchronizationRootDefinitions();
        return changeSummary;
    }

    protected void commitAndWaitForAsyncCompletion() throws Exception {
        TransactionHelper.commitOrRollbackTransaction();
        waitForAsyncCompletion();
        TransactionHelper.startTransaction();

    }

    protected void waitForAsyncCompletion() throws Exception {
        workManager.awaitCompletion(20, TimeUnit.SECONDS);
    }

    protected void setPermissions(DocumentModel doc, ACE... aces) throws Exception {
        ACP acp = session.getACP(doc.getRef());
        ACL localACL = acp.getOrCreateACL(ACL.LOCAL_ACL);
        for (int i = 0; i < aces.length; i++) {
            localACL.add(i, aces[i]);
        }
        session.setACP(doc.getRef(), acp, true);
        commitAndWaitForAsyncCompletion();
    }

    protected void resetPermissions(DocumentModel doc, String userName) throws Exception {
        ACP acp = session.getACP(doc.getRef());
        ACL localACL = acp.getOrCreateACL(ACL.LOCAL_ACL);
        Iterator<ACE> localACLIt = localACL.iterator();
        while (localACLIt.hasNext()) {
            ACE ace = localACLIt.next();
            if (userName.equals(ace.getUsername())) {
                localACLIt.remove();
            }
        }
        session.setACP(doc.getRef(), acp, true);
        commitAndWaitForAsyncCompletion();
    }

    protected abstract void cleanUpAuditLog();

    protected Set<SimpleFileSystemItemChange> toSimpleFileSystemItemChanges(List<FileSystemItemChange> changes) {
        Set<SimpleFileSystemItemChange> simpleChanges = new HashSet<SimpleFileSystemItemChange>();
        for (FileSystemItemChange change : changes) {
            simpleChanges.add(toSimpleFileSystemItemChange(change));
        }
        return simpleChanges;
    }

    protected SimpleFileSystemItemChange toSimpleFileSystemItemChange(FileSystemItemChange change) {
        SimpleFileSystemItemChange simpleChange = new SimpleFileSystemItemChange(change.getDocUuid(),
                change.getEventId(), change.getRepositoryId(), change.getFileSystemItemId(),
                change.getFileSystemItemName());
        DocumentRef changeDocRef = new IdRef(change.getDocUuid());
        if (session.exists(changeDocRef)) {
            simpleChange.setLifeCycleState(session.getDocument(changeDocRef).getCurrentLifeCycleState());
        }
        return simpleChange;
    }

    protected final class SimpleFileSystemItemChange {

        protected String docId;

        protected String eventName;

        protected String repositoryId;

        protected String lifeCycleState;

        protected String fileSystemItemId;

        protected String fileSystemItemName;

        public SimpleFileSystemItemChange(String docId, String eventName) {
            this(docId, eventName, null);
        }

        public SimpleFileSystemItemChange(String docId, String eventName, String repositoryId) {
            this(docId, eventName, repositoryId, null);
        }

        public SimpleFileSystemItemChange(String docId, String eventName, String repositoryId, String fileSystemItemId) {
            this(docId, eventName, repositoryId, fileSystemItemId, null);
        }

        public SimpleFileSystemItemChange(String docId, String eventName, String repositoryId, String fileSystemItemId,
                String fileSystemItemName) {
            this.docId = docId;
            this.eventName = eventName;
            this.repositoryId = repositoryId;
            this.fileSystemItemId = fileSystemItemId;
            this.fileSystemItemName = fileSystemItemName;
        }

        public String getDocId() {
            return docId;
        }

        public String getEventName() {
            return eventName;
        }

        public String getRepositoryId() {
            return repositoryId;
        }

        public String getLifeCycleState() {
            return lifeCycleState;
        }

        public String getFileSystemItemId() {
            return fileSystemItemId;
        }

        public String getFileSystemItemName() {
            return fileSystemItemName;
        }

        public void setLifeCycleState(String lifeCycleState) {
            this.lifeCycleState = lifeCycleState;
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = hash * 37 + docId.hashCode();
            return hash * 37 + eventName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SimpleFileSystemItemChange)) {
                return false;
            }
            SimpleFileSystemItemChange other = (SimpleFileSystemItemChange) obj;
            boolean isEqual = docId.equals(other.getDocId()) && eventName.equals(other.getEventName());
            return isEqual
                    && (repositoryId == null || other.getRepositoryId() == null || repositoryId.equals(other.getRepositoryId()))
                    && (lifeCycleState == null || other.getLifeCycleState() == null || lifeCycleState.equals(other.getLifeCycleState()))
                    && (fileSystemItemId == null || other.getFileSystemItemId() == null || fileSystemItemId.equals(other.getFileSystemItemId()))
                    && (fileSystemItemName == null || other.getFileSystemItemName() == null || fileSystemItemName.equals(other.getFileSystemItemName()));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            sb.append(docId);
            sb.append(", ");
            sb.append(eventName);
            if (repositoryId != null) {
                sb.append(", ");
                sb.append(repositoryId);
            }
            if (lifeCycleState != null) {
                sb.append(", ");
                sb.append(lifeCycleState);
            }
            if (fileSystemItemId != null) {
                sb.append(", ");
                sb.append(fileSystemItemId);
            }
            if (fileSystemItemName != null) {
                sb.append(", ");
                sb.append(fileSystemItemName);
            }
            sb.append(")");
            return sb.toString();
        }
    }

}
