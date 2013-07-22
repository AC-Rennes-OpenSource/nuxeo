/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Olivier Grisel <ogrisel@nuxeo.com>
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.seam;

import java.io.File;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.servlet.ServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.contexts.Context;
import org.jboss.seam.contexts.Contexts;
import org.nuxeo.common.Environment;
import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.RootlessItemException;
import org.nuxeo.drive.hierarchy.userworkspace.adapter.UserWorkspaceHelper;
import org.nuxeo.drive.service.FileSystemItemAdapterService;
import org.nuxeo.drive.service.NuxeoDriveManager;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.security.SecurityException;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.ecm.user.center.UserCenterViewManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 5.7
 */
@Name("nuxeoDriveActions")
@Scope(ScopeType.PAGE)
@Install(precedence = Install.FRAMEWORK)
public class NuxeoDriveActions implements Serializable {

    private static final Log log = LogFactory.getLog(NuxeoDriveActions.class);

    protected static final String IS_UNDER_SYNCHRONIZATION_ROOT = "nuxeoDriveIsUnderSynchronizationRoot";

    protected static final String CURRENT_SYNCHRONIZATION_ROOT = "nuxeoDriveCurrentSynchronizationRoot";

    private static final long serialVersionUID = 1L;

    public static final String NXDRIVE_PROTOCOL = "nxdrive";

    public static final String PROTOCOL_COMMAND_EDIT = "edit";

    protected FileSystemItem currentFileSystemItem;

    @In(required = false)
    NavigationContext navigationContext;

    @In(required = false)
    CoreSession documentManager;

    @In(required = false, create = true)
    UserCenterViewManager userCenterViews;

    @Factory(value = CURRENT_SYNCHRONIZATION_ROOT, scope = ScopeType.EVENT)
    public DocumentModel getCurrentSynchronizationRoot() throws ClientException {
        if (navigationContext == null || documentManager == null) {
            return null;
        }
        // Use the event context as request cache
        Context cache = Contexts.getEventContext();
        Boolean isUnderSync = (Boolean) cache.get(IS_UNDER_SYNCHRONIZATION_ROOT);
        if (isUnderSync == null) {
            NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
            Set<IdRef> references = driveManager.getSynchronizationRootReferences(documentManager);
            DocumentModelList path = navigationContext.getCurrentPath();
            DocumentModel root = null;
            // list is ordered such as closest synchronized ancestor is
            // considered the current synchronization root
            for (DocumentModel parent : path) {
                if (references.contains(parent.getRef())) {
                    root = parent;
                    break;
                }
            }
            cache.set(CURRENT_SYNCHRONIZATION_ROOT, root);
            cache.set(IS_UNDER_SYNCHRONIZATION_ROOT, root != null);
        }
        return (DocumentModel) cache.get(CURRENT_SYNCHRONIZATION_ROOT);
    }

    @Factory(value = "canEditCurrentDocument", scope = ScopeType.EVENT)
    public boolean canEditCurrentDocument() throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (currentDocument.isFolder()) {
            return false;
        }
        if (getCurrentSynchronizationRoot() == null) {
            return false;
        }
        // Check if current document can be adapted as a FileSystemItem
        return getCurrentFileSystemItem() != null;
    }

    /**
     * {@link #NXDRIVE_PROTOCOL} must be handled by a protocol handler
     * configured on the client side (either on the browser, or on the OS).
     *
     * @return Drive edit URL in the form "{@link #NXDRIVE_PROTOCOL}://
     *         {@link #PROTOCOL_COMMAND_EDIT}
     *         /protocol/server[:port]/webappName/nxdoc/repoName/docRef"
     * @throws ClientException
     *
     */
    public String getDriveEditURL() throws ClientException {
        // Current document must be adaptable as a FileSystemItem
        if (getCurrentFileSystemItem() == null) {
            throw new ClientException(
                    String.format(
                            "Document %s (%s) is not adaptable as a FileSystemItem thus not Drive editable, \"driveEdit\" action should not be displayed.",
                            navigationContext.getCurrentDocument().getId(),
                            navigationContext.getCurrentDocument().getPathAsString()));
        }
        String fsItemId = currentFileSystemItem.getId();
        ServletRequest servletRequest = (ServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        String baseURL = VirtualHostHelper.getBaseURL(servletRequest);
        StringBuffer sb = new StringBuffer();
        sb.append(NXDRIVE_PROTOCOL).append("://");
        sb.append(PROTOCOL_COMMAND_EDIT).append("/");
        sb.append(baseURL.replaceFirst("://", "/"));
        sb.append("fsitem/");
        sb.append(fsItemId);
        return sb.toString();
    }

    @Factory(value = "canSynchronizeCurrentDocument", scope = ScopeType.EVENT)
    public boolean getCanSynchronizeCurrentDocument() throws ClientException {
        if (navigationContext == null) {
            return false;
        }
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (!currentDocument.isFolder()) {
            return false;
        }
        boolean hasPermission = documentManager.hasPermission(
                currentDocument.getRef(), SecurityConstants.ADD_CHILDREN);
        if (!hasPermission) {
            return false;
        }
        return getCurrentSynchronizationRoot() == null;
    }

    @Factory(value = "canUnSynchronizeCurrentDocument", scope = ScopeType.EVENT)
    public boolean getCanUnSynchronizeCurrentDocument() throws ClientException {
        if (navigationContext == null) {
            return false;
        }
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        DocumentRef currentDocRef = currentDocument.getRef();
        DocumentModel currentSyncRoot = getCurrentSynchronizationRoot();
        if (currentSyncRoot == null) {
            return false;
        }
        return currentDocRef.equals(currentSyncRoot.getRef());
    }

    @Factory(value = "canNavigateToCurrentSynchronizationRoot", scope = ScopeType.EVENT)
    public boolean getCanNavigateToCurrentSynchronizationRoot()
            throws ClientException {
        if (navigationContext == null) {
            return false;
        }
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        DocumentRef currentDocRef = currentDocument.getRef();
        DocumentModel currentSyncRoot = getCurrentSynchronizationRoot();
        if (currentSyncRoot == null) {
            return false;
        }
        return !currentDocRef.equals(currentSyncRoot.getRef());
    }

    @Factory(value = "currentDocumentUserWorkspace", scope = ScopeType.PAGE)
    public boolean isCurrentDocumentUserWorkspace() throws ClientException {
        if (navigationContext == null) {
            return false;
        }
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        return UserWorkspaceHelper.isUserWorkspace(currentDocument);
    }

    public String synchronizeCurrentDocument() throws ClientException,
            SecurityException {
        NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
        Principal principal = documentManager.getPrincipal();
        String userName = principal.getName();
        DocumentModel newSyncRoot = navigationContext.getCurrentDocument();
        driveManager.registerSynchronizationRoot(principal, newSyncRoot,
                documentManager);
        TokenAuthenticationService tokenService = Framework.getLocalService(TokenAuthenticationService.class);
        boolean hasOneNuxeoDriveToken = false;
        for (DocumentModel token : tokenService.getTokenBindings(userName)) {
            if ("Nuxeo Drive".equals(token.getPropertyValue("authtoken:applicationName"))) {
                hasOneNuxeoDriveToken = true;
                break;
            }
        }
        if (hasOneNuxeoDriveToken) {
            return null;
        } else {
            // redirect to user center
            userCenterViews.setCurrentViewId("userCenterNuxeoDrive");
            return "view_home";
        }
    }

    public void unsynchronizeCurrentDocument() throws ClientException {
        NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
        Principal principal = documentManager.getPrincipal();
        DocumentModel syncRoot = navigationContext.getCurrentDocument();
        driveManager.unregisterSynchronizationRoot(principal, syncRoot,
                documentManager);
    }

    public String navigateToCurrentSynchronizationRoot() throws ClientException {
        DocumentModel currentRoot = getCurrentSynchronizationRoot();
        if (currentRoot == null) {
            return "";
        }
        return navigationContext.navigateToDocument(currentRoot);
    }

    public DocumentModelList getSynchronizationRoots() throws ClientException {
        DocumentModelList syncRoots = new DocumentModelListImpl();
        NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
        Set<IdRef> syncRootRefs = driveManager.getSynchronizationRootReferences(documentManager);
        for (IdRef syncRootRef : syncRootRefs) {
            syncRoots.add(documentManager.getDocument(syncRootRef));
        }
        return syncRoots;
    }

    public void unsynchronizeRoot(DocumentModel syncRoot)
            throws ClientException {
        NuxeoDriveManager driveManager = Framework.getLocalService(NuxeoDriveManager.class);
        Principal principal = documentManager.getPrincipal();
        driveManager.unregisterSynchronizationRoot(principal, syncRoot,
                documentManager);
    }

    @Factory(value = "nuxeoDriveClientPackages", scope = ScopeType.CONVERSATION)
    public List<DesktopPackageDefinition> getClientPackages() {

        List<DesktopPackageDefinition> packages = new ArrayList<DesktopPackageDefinition>();
        // Add packages from the client directory
        File clientDir = new File(Environment.getDefault().getServerHome(),
                "client");
        if (clientDir.isDirectory()) {
            for (File file : clientDir.listFiles()) {
                String fileName = file.getName();
                boolean isDesktopPackage = false;
                String platform = null;
                if (fileName.endsWith(".msi")) {
                    isDesktopPackage = true;
                    platform = "windows";
                } else if (fileName.endsWith(".dmg")) {
                    isDesktopPackage = true;
                    platform = "osx";
                } else if (fileName.endsWith(".deb")) {
                    isDesktopPackage = true;
                    platform = "ubuntu";
                }
                if (isDesktopPackage) {
                    packages.add(new DesktopPackageDefinition(file, fileName,
                            platform));
                    log.debug(String.format(
                            "Added %s to the list of desktop packages available for download.",
                            fileName));
                }
            }
        }
        // Add external links
        // TODO: remove when Debian package is available
        packages.add(new DesktopPackageDefinition(
                "https://github.com/nuxeo/nuxeo-drive/#ubuntudebian-and-other-linux-variants-client",
                "user.center.nuxeoDrive.platform.ubuntu.docLinkTitle", "ubuntu"));
        return packages;
    }

    public String downloadClientPackage(String name, File file) {
        FacesContext facesCtx = FacesContext.getCurrentInstance();
        return ComponentUtils.downloadFile(facesCtx, name, file);
    }

    protected FileSystemItem getCurrentFileSystemItem() throws ClientException {
        if (currentFileSystemItem == null) {
            // TODO: optim: add a new method to FileSystemItemAdapterService to
            // quickly compute the fsitem id from a doc (without having to
            // recursively adapt the parents)
            DocumentModel currentDocument = navigationContext.getCurrentDocument();
            try {
                currentFileSystemItem = Framework.getLocalService(
                        FileSystemItemAdapterService.class).getFileSystemItem(
                        currentDocument);
            } catch (RootlessItemException e) {
                log.debug(String.format(
                        "RootlessItemException thrown while trying to adapt document %s (%s) as a FileSystemItem.",
                        currentDocument.getId(),
                        currentDocument.getPathAsString()));
            }
            if (currentFileSystemItem == null) {
                log.debug(String.format(
                        "Document %s (%s) is not adaptable as a FileSystemItem => currentFileSystemItem is null.",
                        currentDocument.getId(),
                        currentDocument.getPathAsString()));
            }
        }
        return currentFileSystemItem;
    }

}
