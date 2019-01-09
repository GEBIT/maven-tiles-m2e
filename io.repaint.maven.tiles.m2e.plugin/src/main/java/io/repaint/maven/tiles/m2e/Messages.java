//
// Messages.java
//
// Copyright (C) 2019
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package io.repaint.maven.tiles.m2e;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

	private static final String BUNDLE_NAME = "io.repaint.maven.tiles.m2e.messages"; //$NON-NLS-1$

	public static String TileHyperlinkDetector_openTile;

	public static String TileHyperlinkDetector_errorMessage;

	public static String TileHyperlinkDetector_errorTitle;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {}
}
