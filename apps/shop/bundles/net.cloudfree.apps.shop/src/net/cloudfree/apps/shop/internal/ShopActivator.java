/**
 * Copyright (c) 2010 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 */
package net.cloudfree.apps.shop.internal;

import java.util.concurrent.atomic.AtomicReference;

import net.cloudfree.apps.shop.internal.app.ShopApplicationProvider;

import org.eclipse.gyrex.common.runtime.BaseBundleActivator;
import org.eclipse.gyrex.http.application.provider.ApplicationProvider;
import org.osgi.framework.BundleContext;

public class ShopActivator extends BaseBundleActivator {

	public static final String SYMBOLIC_NAME = "net.cloudfree.apps.shop";

	private static final AtomicReference<ShopActivator> instance = new AtomicReference<ShopActivator>();

	public static ShopActivator getInstance() {
		final ShopActivator activator = instance.get();
		if (null == activator) {
			throw new IllegalStateException("inactive");
		}
		return activator;
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param symbolicName
	 */
	public ShopActivator() {
		super(SYMBOLIC_NAME);
	}

	@Override
	protected void doStart(final BundleContext context) throws Exception {
		instance.set(this);

		// register fan shop provider
		getServiceHelper().registerService(ApplicationProvider.class.getName(), new ShopApplicationProvider(), "CloudFree.net", "Application provider for the CloudFree shop application.", null, null);
	}

	@Override
	protected void doStop(final BundleContext context) throws Exception {
		instance.set(null);
	}
}
