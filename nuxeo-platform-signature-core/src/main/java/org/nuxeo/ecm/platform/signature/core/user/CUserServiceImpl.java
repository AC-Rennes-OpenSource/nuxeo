/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *    Wojciech Sulejman
 */
package org.nuxeo.ecm.platform.signature.core.user;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.Base64;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.signature.api.exception.CertException;
import org.nuxeo.ecm.platform.signature.api.pki.CertService;
import org.nuxeo.ecm.platform.signature.api.pki.RootService;
import org.nuxeo.ecm.platform.signature.api.user.AliasType;
import org.nuxeo.ecm.platform.signature.api.user.AliasWrapper;
import org.nuxeo.ecm.platform.signature.api.user.CNField;
import org.nuxeo.ecm.platform.signature.api.user.CUserService;
import org.nuxeo.ecm.platform.signature.api.user.UserInfo;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Base implementation of the user certificate service.
 *
 * @author <a href="mailto:ws@nuxeo.com">Wojciech Sulejman</a>
 */
public class CUserServiceImpl extends DefaultComponent implements CUserService {

    private static final Log LOG = LogFactory.getLog(CUserServiceImpl.class);

    private static final String CERTIFICATE_DIRECTORY_NAME = "certificate";

    protected RootService rootService;

    protected CertService certService;

    /**
     * Configurable country code
     */
    protected String countryCode;

    /**
     * Configurable organization name
     */
    protected String organization;

    /**
     * Configurable organizational unit name
     */
    protected String organizationalUnit;

    @Override
    public UserInfo getUserInfo(DocumentModel userModel) throws CertException {
        UserInfo userInfo = null;
        String userID = (String) userModel.getPropertyValue("user:username");
        String firstName = (String) userModel.getPropertyValue("user:firstName");
        String lastName = (String) userModel.getPropertyValue("user:lastName");
        String email = (String) userModel.getPropertyValue("user:email");

        Map<CNField, String> userFields = new HashMap<CNField, String>();

        userFields.put(CNField.C, countryCode);
        userFields.put(CNField.O, organization);
        userFields.put(CNField.OU, organizationalUnit);

        userFields.put(CNField.CN, firstName + " " + lastName);
        userFields.put(CNField.Email, email);
        userFields.put(CNField.UserID, userID);
        userInfo = new UserInfo(userFields);
        return userInfo;
    }

    @Override
    public KeyStore getUserKeystore(String userID, String userKeystorePassword) throws CertException {
        // Log in as system user
        LoginContext lc;
        try {
            lc = Framework.login();
        } catch (LoginException e) {
            throw new NuxeoException("Cannot log in as system user", e);
        }
        try {
            // Open directory session
            try (Session session = getDirectoryService().open(CERTIFICATE_DIRECTORY_NAME)) {
                KeyStore keystore = null;
                DocumentModel entry = session.getEntry(userID);
                if (entry != null) {
                    String keystore64Encoded = (String) entry.getPropertyValue("cert:keystore");
                    byte[] keystoreBytes = Base64.decode(keystore64Encoded);
                    ByteArrayInputStream byteIS = new ByteArrayInputStream(keystoreBytes);
                    keystore = getCertService().getKeyStore(byteIS, userKeystorePassword);
                } else {
                    throw new CertException("No directory entry for " + userID);
                }
                return keystore;
            }
        } finally {
            try {
                // Login context may be null in tests
                if (lc != null) {
                    lc.logout();
                }
            } catch (LoginException e) {
                throw new NuxeoException("Cannot log out system user", e);
            }
        }
    }

    @Override
    public DocumentModel createCertificate(DocumentModel user, String userKeyPassword) throws CertException {
        // Log in as system user
        LoginContext lc;
        try {
            lc = Framework.login();
        } catch (LoginException e) {
            throw new NuxeoException("Cannot log in as system user", e);
        }
        try {
            try (Session session = getDirectoryService().open(CERTIFICATE_DIRECTORY_NAME)) {
                String userKeystorePassword = userKeyPassword;
                DocumentModel certificate = null;

                // create an entry in the directory
                String userID = (String) user.getPropertyValue("user:username");

                // make sure that no certificates are associated with the
                // current userid
                boolean certificateExists = session.hasEntry(userID);
                if (certificateExists) {
                    throw new CertException(userID + " already has a certificate");
                }

                LOG.info("Starting certificate generation for: " + userID);
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("userid", userID);

                // add a keystore to a directory entry
                KeyStore keystore = getCertService().initializeUser(getUserInfo(user), userKeyPassword);
                ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
                getCertService().storeCertificate(keystore, byteOS, userKeystorePassword);
                String keystore64Encoded = Base64.encodeBytes(byteOS.toByteArray());
                map.put("keystore", keystore64Encoded);
                map.put("certificate", getUserCertInfo(keystore, user));
                map.put("keypassword", userKeyPassword);
                certificate = session.createEntry(map);
                return certificate;
            } catch (DirectoryException e) {
                LOG.error(e);
                throw new CertException(e);
            }
        } finally {
            try {
                // Login context may be null in tests
                if (lc != null) {
                    lc.logout();
                }
            } catch (LoginException e) {
                throw new NuxeoException("Cannot log out system user", e);
            }
        }
    }

    protected static DirectoryService getDirectoryService() {
        return Framework.getService(DirectoryService.class);
    }

    @Override
    public String getUserCertInfo(DocumentModel user, String userKeyPassword) throws CertException {
        String userKeystorePassword = userKeyPassword;
        String userID = (String) user.getPropertyValue("user:username");
        KeyStore keystore = getUserKeystore(userID, userKeystorePassword);
        return getUserCertInfo(keystore, user);
    }

    private String getUserCertInfo(KeyStore keystore, DocumentModel user) throws CertException {
        String userCertInfo = null;
        if (null != keystore) {
            String userID = (String) user.getPropertyValue("user:username");
            AliasWrapper alias = new AliasWrapper(userID);
            X509Certificate certificate = getCertService().getCertificate(keystore, alias.getId(AliasType.CERT));
            userCertInfo = certificate.getSubjectDN() + " valid till: " + certificate.getNotAfter();
        }
        return userCertInfo;
    }

    @Override
    public DocumentModel getCertificate(String userID) {
        // Log in as system user
        LoginContext lc;
        try {
            lc = Framework.login();
        } catch (LoginException e) {
            throw new NuxeoException("Cannot log in as system user", e);
        }
        try {
            // Open directory session
            try (Session session = getDirectoryService().open(CERTIFICATE_DIRECTORY_NAME)) {
                DocumentModel certificate = session.getEntry(userID);
                return certificate;
            }
        } finally {
            try {
                // Login context may be null in tests
                if (lc != null) {
                    lc.logout();
                }
            } catch (LoginException e) {
                throw new NuxeoException("Cannot log out system user", e);
            }
        }
    }

    @Override
    public byte[] getRootCertificateData() {
        byte[] certificateData = getRootService().getRootPublicCertificate();
        return certificateData;
    }

    @Override
    public boolean hasCertificate(String userID) throws CertException {
        // Log in as system user
        LoginContext lc;
        try {
            lc = Framework.login();
        } catch (LoginException e) {
            throw new NuxeoException("Cannot log in as system user", e);
        }
        try {
            // Open directory session
            try (Session session = getDirectoryService().open(CERTIFICATE_DIRECTORY_NAME)) {
                return session.getEntry(userID) != null;
            }
        } finally {
            try {
                // Login context may be null in tests
                if (lc != null) {
                    lc.logout();
                }
            } catch (LoginException e) {
                throw new NuxeoException("Cannot log out system user", e);
            }
        }
    }

    @Override
    public void deleteCertificate(String userID) throws CertException {
        // Log in as system user
        LoginContext lc;
        try {
            lc = Framework.login();
        } catch (LoginException e) {
            throw new NuxeoException("Cannot log in as system user", e);
        }
        try {
            // Open directory session
            try (Session session = getDirectoryService().open(CERTIFICATE_DIRECTORY_NAME)) {
                DocumentModel certEntry = session.getEntry(userID);
                session.deleteEntry(certEntry);
                assert (null == session.getEntry(userID));
            }
        } finally {
            try {
                // Login context may be null in tests
                if (lc != null) {
                    lc.logout();
                }
            } catch (LoginException e) {
                throw new NuxeoException("Cannot log out system user", e);
            }
        }
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof CUserDescriptor) {
            CUserDescriptor desc = (CUserDescriptor) contribution;
            countryCode = desc.getCountryCode();
            organization = desc.getOrganization();
            organizationalUnit = desc.getOrganizationalUnit();
        }
    }

    protected CertService getCertService() {
        if (certService == null) {
            certService = Framework.getService(CertService.class);
        }
        return certService;
    }

    protected RootService getRootService() {
        if (rootService == null) {
            rootService = Framework.getService(RootService.class);
        }
        return rootService;
    }
}
