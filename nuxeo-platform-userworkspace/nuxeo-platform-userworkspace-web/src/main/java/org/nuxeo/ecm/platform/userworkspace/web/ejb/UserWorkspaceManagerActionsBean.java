/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     btatar
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.userworkspace.web.ejb;

import static org.jboss.seam.ScopeType.SESSION;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Destroy;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.Startup;
import org.jboss.seam.core.Events;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.api.WebActions;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceManagerActions;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.platform.userworkspace.constants.UserWorkspaceConstants;
import org.nuxeo.ecm.webapp.action.MainTabsActions;
import org.nuxeo.ecm.webapp.dashboard.DashboardNavigationHelper;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;

/**
 * Personal user workspace manager actions bean.
 *
 * @author btatar
 */
@Name("userWorkspaceManagerActions")
@Scope(SESSION)
@Install(precedence = Install.FRAMEWORK)
@Startup
public class UserWorkspaceManagerActionsBean implements
        UserWorkspaceManagerActions {

    public static final String DOCUMENT_VIEW = "view_documents";
    public static final String DOCUMENT_MANAGEMENT_ACTION = "documents";

    private static final long serialVersionUID = 1828552026739219850L;

    private static final Log log = LogFactory.getLog(UserWorkspaceManagerActions.class);

    protected static final String DOCUMENT_MANAGEMENT_TAB = WebActions.MAIN_TABS_CATEGORY
            + ":" + WebActions.DOCUMENTS_MAIN_TAB_ID;

    protected boolean showingPersonalWorkspace;

    protected boolean initialized;

    protected DocumentModel lastAccessedDocument;

    // Rux INA-252: very likely cause of passivation error
    protected transient UserWorkspaceService userWorkspaceService;

    // Rux INA-252: another cause of passivation error
    @In(required = true)
    protected transient NavigationContext navigationContext;

    @In(required = false, create = true)
    protected transient CoreSession documentManager;

    @In(create = true)
    protected transient DashboardNavigationHelper dashboardNavigationHelper;

    @In(create = true)
    protected transient MainTabsActions mainTabsActions;

    @In(create = true)
    protected transient WebActions webActions;

    public void initialize() {
        log.debug("Initializing user workspace manager actions bean");
        try {
            // Rux INA-252: use a getter
            // userWorkspaceService =
            // Framework.getLocalService(UserWorkspaceService.class);
            showingPersonalWorkspace = false;
            initialized = true;
        } catch (Exception e) {
            log.error(
                    "There was an error while trying to get UserWorkspaceService",
                    e);
        }
    }

    @Destroy
    public void destroy() {
        userWorkspaceService = null;
        log.debug("Removing user workspace actions bean");
    }

    private UserWorkspaceService getUserWorkspaceService() {
        if (userWorkspaceService != null) {
            return userWorkspaceService;
        }
        userWorkspaceService = Framework.getLocalService(UserWorkspaceService.class);
        return userWorkspaceService;
    }

    public DocumentModel getCurrentUserPersonalWorkspace()
            throws ClientException {
        if (!initialized) {
            initialize();
        }
        // protection in case we have not yet chosen a repository. if not
        // repository, then there is no documentManager(session)
        if (documentManager == null) {
            return null;// this is ok because it eventually will be
            // dealt with by setCurrentDocument, which will deal with
            // the lack of a documentManager
        }
        return getUserWorkspaceService().getCurrentUserPersonalWorkspace(
                documentManager, navigationContext.getCurrentDocument());
    }

    public String navigateToCurrentUserPersonalWorkspace()
            throws ClientException {
        if (!initialized) {
            initialize();
        }
        String returnView = DOCUMENT_VIEW;

        // force return to Documents tab
        webActions.setCurrentTabId(WebActions.MAIN_TABS_CATEGORY, DOCUMENT_MANAGEMENT_ACTION);

        // Rux INA-221: separated links for going to workspaces
        DocumentModel currentUserPersonalWorkspace = getCurrentUserPersonalWorkspace();
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (!isShowingPersonalWorkspace() && currentDocument != null
                && currentDocument.getPath().segment(0) != null) {
            lastAccessedDocument = mainTabsActions.getDocumentFor(
                    DOCUMENT_MANAGEMENT_ACTION, navigationContext.getCurrentDocument());
        }
        navigationContext.setCurrentDocument(currentUserPersonalWorkspace);
        showingPersonalWorkspace = true;

        Events.instance().raiseEvent(EventNames.GO_PERSONAL_WORKSPACE);

        return returnView;
    }

    // Rux INA-221: create a new method for the 2 separated links
    public String navigateToOverallWorkspace() throws ClientException {
        if (!initialized) {
            initialize();
        }
        String returnView = DOCUMENT_VIEW;

        // force return to Documents tab
        webActions.setCurrentTabIds(DOCUMENT_MANAGEMENT_TAB);

        if (lastAccessedDocument != null) {
            navigationContext.setCurrentDocument(lastAccessedDocument);
        } else if (navigationContext.getCurrentDomain() != null) {
            navigationContext.setCurrentDocument(navigationContext.getCurrentDomain());
        } else if (documentManager.hasPermission(documentManager.getRootDocument().getRef(),
                SecurityConstants.READ_CHILDREN)) {
            navigationContext.setCurrentDocument(documentManager.getRootDocument());
        } else {
            navigationContext.setCurrentDocument(null);
            returnView = dashboardNavigationHelper.navigateToDashboard();
        }
        showingPersonalWorkspace = false;

        Events.instance().raiseEvent(EventNames.GO_HOME);

        return returnView;
    }

    @Factory(value = "isInsidePersonalWorkspace", scope = ScopeType.EVENT)
    public boolean isShowingPersonalWorkspace() {
        if (!initialized) {
            initialize();
        }

        // NXP-9813 : navigating to a tab different than Documents should not change
        // the value for showingPersonalWorkspace
        if (mainTabsActions.isOnMainTab(DOCUMENT_MANAGEMENT_ACTION)) {
            DocumentModel currentDoc = navigationContext.getCurrentDocument();

            if (currentDoc == null || currentDoc.getPath().segmentCount() < 2) {
                return false;
            }

            String secondSegment = currentDoc.getPath().segment(1);
            showingPersonalWorkspace = secondSegment != null
                    && secondSegment.startsWith(UserWorkspaceConstants.USERS_PERSONAL_WORKSPACES_ROOT);
        }
        return showingPersonalWorkspace;
    }

    public void setShowingPersonalWorkspace(boolean showingPersonalWorkspace) {
        this.showingPersonalWorkspace = showingPersonalWorkspace;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

}
