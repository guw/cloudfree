/*******************************************************************************
 * Copyright (c) 2008,2010 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package net.cloudfree.apps.shop.internal.app;

import javax.servlet.ServletException;

import net.cloudfree.apps.shop.internal.ShopActivator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.http.application.Application;

/**
 * A shop application instance.
 */
public class ShopApplication extends Application {

	ShopApplication(final String id, final IRuntimeContext context) {
		super(id, context);
	}

	@Override
	protected void doDestroy() {
		// empty
	}

	@Override
	protected void doInit() throws CoreException {
		try {
			// register the  listing servlet
			getApplicationServiceSupport().registerServlet("/listings", new JsonListingServlet(getContext()), null);
		} catch (final ServletException e) {
			throw new CoreException(ShopActivator.getInstance().getStatusUtil().createError(0, e.getMessage(), e));
		}

	}
}
