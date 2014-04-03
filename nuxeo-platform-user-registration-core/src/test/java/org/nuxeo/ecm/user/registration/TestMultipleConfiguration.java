package org.nuxeo.ecm.user.registration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.user.invite.RegistrationRules.FIELD_ALLOW_USER_CREATION;
import static org.nuxeo.ecm.user.invite.UserRegistrationConfiguration.DEFAULT_CONFIGURATION_NAME;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.user.invite.RegistrationRules;
import org.nuxeo.ecm.user.invite.UserRegistrationConfiguration;
import org.nuxeo.ecm.user.invite.UserRegistrationInfo;

/**
 * @author <a href="mailto:akervern@nuxeo.com">Arnaud Kervern</a>
 */
public class TestMultipleConfiguration extends AbstractUserRegistration {

    @Test
    public void testMultipleUserRegistration() throws ClientException {
        initializeRegistrations();

        // Create workspaces where users will be invited
        DocumentModel testWorkspace = session.createDocumentModel(
                "/default-domain", "testWorkspace", "Workspace");
        testWorkspace.setPropertyValue("dc:title", "Test Workspace");
        String workspaceId = session.createDocument(testWorkspace).getId();
        session.save();

        assertEquals(0, userManager.searchUsers("testUser").size());
        assertEquals(0, userManager.searchUsers("testUser2").size());

        // User info
        UserRegistrationInfo userInfo = new UserRegistrationInfo();
        userInfo.setLogin("testUser");
        userInfo.setEmail("dummy@test.com");

        // Doc info
        DocumentRegistrationInfo docInfo = new DocumentRegistrationInfo();
        docInfo.setDocumentId(testWorkspace.getId());
        docInfo.setPermission(SecurityConstants.READ_WRITE);

        // Invite first user with defautl conf
        String requestId = userRegistrationService.submitRegistrationRequest(
                DEFAULT_CONFIGURATION_NAME, userInfo, docInfo,
                new HashMap<String, Serializable>(),
                UserRegistrationService.ValidationMethod.NONE, true);
        userRegistrationService.validateRegistration(requestId, new HashMap<String, Serializable>());

        // Invite second user with test conf
        userInfo.setLogin("testUser2");
        requestId = userRegistrationService.submitRegistrationRequest("test",
                userInfo, docInfo, new HashMap<String, Serializable>(),
                UserRegistrationService.ValidationMethod.NONE, true);
        userRegistrationService.validateRegistration(requestId, new HashMap<String, Serializable>());

        session.save();

        // Default registration container
        assertEquals(1, session.getChildren(new PathRef("/requests")).size());
        // Test registration container
        assertEquals(1, session.getChildren(new PathRef("/test-requests")).size());

        assertNotNull(userManager.getUserModel("testUser"));
        assertNotNull(userManager.getUserModel("testUser2"));
    }
}
