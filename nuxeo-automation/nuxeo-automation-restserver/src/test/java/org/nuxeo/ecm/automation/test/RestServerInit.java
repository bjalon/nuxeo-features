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
 *     dmetzler
 */
package org.nuxeo.ecm.automation.test;

import org.nuxeo.ecm.automation.test.adapters.BusinessBeanAdapter;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * Repo init to test Rest API
 *
 *
 * @since 5.7.2
 */
public class RestServerInit implements RepositoryInit {

    public static final String[] FIRSTNAMES = { "Steve", "John", "Georges",
            "Bill" };

    public static final String[] LASTNAMES = { "Jobs", "Lennon", "Harrisson",
            "Gates" };

    @Override
    public void populate(CoreSession session) throws ClientException {
        // Create some docs
        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/", "folder_" + i,
                    "Folder");
            doc.setPropertyValue("dc:title", "Folder " + i);
            doc = session.createDocument(doc);
        }

        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/folder_1",
                    "note_" + i, "Note");
            doc.setPropertyValue("dc:title", "Note " + i);

            doc.getAdapter(BusinessBeanAdapter.class).setNote("Note " + i);
            doc = session.createDocument(doc);
        }

        session.save();

        // Create some users
        for (int idx = 0; idx < 4; idx++) {
            String userId = "user" + idx;

            UserManager um = Framework.getLocalService(UserManager.class);

            NuxeoPrincipal principal = um.getPrincipal(userId);

            if (principal == null) {

                DocumentModel userModel = um.getBareUserModel();
                String schemaName = um.getUserSchemaName();
                userModel.setProperty(schemaName, "username", userId);
                userModel.setProperty(schemaName, "firstName", FIRSTNAMES[idx]);
                userModel.setProperty(schemaName, "lastName", LASTNAMES[idx]);
                userModel = um.createUser(userModel);
                principal = um.getPrincipal(userId);
            }
        }

    }

    public static DocumentModel getFolder(int index, CoreSession session)
            throws ClientException {
        return session.getDocument(new PathRef("/folder_" + index));
    }

    public static DocumentModel getNote(int index, CoreSession session)
            throws ClientException {
        return session.getDocument(new PathRef("/folder_1/note_" + index));
    }

}