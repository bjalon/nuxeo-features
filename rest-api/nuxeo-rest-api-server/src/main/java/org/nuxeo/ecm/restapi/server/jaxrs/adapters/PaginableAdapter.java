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
package org.nuxeo.ecm.restapi.server.jaxrs.adapters;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.core.util.Paginable;
import org.nuxeo.ecm.automation.core.util.PaginablePageProvider;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.webengine.model.impl.DefaultAdapter;
import org.nuxeo.runtime.api.Framework;

/**
 * Abstract adapter to be used when one want to contribute
 * an adapter base on PageProviders. In order to use it,
 * just override the {@link PaginableAdapter#getPageProviderDefinition()}
 * and {@link PaginableAdapter#getParams()}
 *
 * @since 5.7.2
 */
/**
 * Abstract adapter to be used when one want to contribute an adapter base on
 * PageProviders.
 * <p>
 * In order to use it, just override the
 * {@link PaginableAdapter#getPageProviderDefinition()} and
 * {@link PaginableAdapter#getParams()}
 *
 * @since 5.7.2
 */
public abstract class PaginableAdapter<T> extends DefaultAdapter {

    protected Long currentPageIndex;

    protected Long pageSize;

    protected String maxResults;

    /**
     * Sort by parameters (can be a list of sorts, separated by commas).
     * <p>
     * Exp: dc:title,dc:modified.
     *
     * @since 5.9.4
     */
    protected String sortBy;

    /**
     * Sort order parameters (can be a list of sorts orders, separated by
     * commas, matched by index to corresponding sort by parameters).
     * <p>
     * Exp: asc,desc, or ASC,DESC. When empty, defaults to 'desc'.
     *
     * @since 5.9.4
     */
    protected String sortOrder;

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        final HttpServletRequest request = ctx.getRequest();

        currentPageIndex = extractLongParam(request, "currentPageIndex", 0L);
        pageSize = extractLongParam(request, "pageSize", 50L);
        maxResults = request.getParameter("maxResults");
        sortBy = request.getParameter("sortBy");
        sortOrder = request.getParameter("sortOrder");
    }

    @Override
    public <A> A getAdapter(Class<A> adapter) {
        if (adapter.isAssignableFrom(DocumentModelList.class)) {
            try {
                return adapter.cast(getPaginableEntries());
            } catch (ClientException e) {
                return null;
            }
        }
        return super.getAdapter(adapter);
    }

    abstract protected PageProviderDefinition getPageProviderDefinition();

    protected Object[] getParams() {
        return new Object[] {};
    }

    protected DocumentModel getSearchDocument() throws ClientException {
        return null;
    }

    @SuppressWarnings("unchecked")
    @GET
    public Paginable<T> getPaginableEntries() throws ClientException {
        PageProviderDefinition ppDefinition = getPageProviderDefinition();
        if (ppDefinition == null) {
            throw new ClientException("Page provider given not found");
        }

        PageProviderService pps = Framework.getLocalService(PageProviderService.class);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (Serializable) ctx.getCoreSession());
        PageProvider<T> pp = (PageProvider<T>) pps.getPageProvider("",
                ppDefinition, getSearchDocument(), null, pageSize,
                currentPageIndex, props, getParams());
        if (!StringUtils.isBlank(sortBy)) {
            String[] sorts = sortBy.split(",");
            String[] orders = null;
            if (!StringUtils.isBlank(sortOrder)) {
                orders = sortOrder.split(",");
            }
            if (sorts != null) {
                // clear potential default sort infos first
                pp.setSortInfos(null);
                for (int i = 0; i < sorts.length; i++) {
                    String sort = sorts[i];
                    boolean sortAscending = (orders != null
                            && orders.length > i && "asc".equals(orders[i].toLowerCase())) ? true
                            : false;
                    pp.addSortInfo(sort, sortAscending);
                }
            }
        }
        return getPaginableEntries(pp);
    }

    protected Paginable<T> getPaginableEntries(PageProvider<T> pageProvider) {
        return new PaginablePageProvider<>(pageProvider);
    }

    protected Long extractLongParam(HttpServletRequest request,
            String paramName, Long defaultValue) {
        String strParam = request.getParameter(paramName);
        return strParam == null ? defaultValue : Long.parseLong(strParam);
    }

}
