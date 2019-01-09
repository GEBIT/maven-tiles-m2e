//
// TilesHyperlinkDetector.java
//
// Copyright (C) 2019
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package io.repaint.maven.tiles.m2e;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.ui.internal.actions.OpenPomAction.MavenPathStorageEditorInput;
import org.eclipse.m2e.core.ui.internal.search.util.Packaging;
import org.eclipse.m2e.editor.xml.internal.NodeOperation;
import org.eclipse.m2e.editor.xml.internal.XmlUtils;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Hyperlink detector for XML editor to handle &lt;tile&gt; references.
 */
public class TilesHyperlinkDetector extends AbstractHyperlinkDetector {

	private static final String TILE_XML = "tile.xml";

	private static final String XML_EXTENSION = ".xml";

	@Override
	public IHyperlink[] detectHyperlinks(final ITextViewer textViewer, final IRegion region,
			boolean canShowMultipleHyperlinks) {
		if (region == null || textViewer == null) {
			return null;
		}

		IDocument document = textViewer.getDocument();
		if (document == null) {
			return null;
		}

		IRegion lineInfo;
		String line;
		try {
			lineInfo = document.getLineInformationOfOffset(region.getOffset());
			line = document.get(lineInfo.getOffset(), lineInfo.getLength());
		} catch (BadLocationException ex) {
			return null;
		}

		if (line.length() == 0) {
			return null;
		}
		final List<IHyperlink> hyperlinks = new ArrayList<IHyperlink>();
		final int offset = region.getOffset();

		XmlUtils.performOnCurrentElement(document, offset, new NodeOperation<Node>() {

			@Override
			public void process(Node node, IStructuredDocument structured) {
				// check if we have a property expression at cursor
				IHyperlink link = openTile(node, textViewer, offset);
				if (link != null) {
					hyperlinks.add(link);
				}
			}
		});

		if (hyperlinks.size() > 0) {
			return hyperlinks.toArray(new IHyperlink[0]);
		}
		return null;
	}

	private IHyperlink openTile(Node current, final ITextViewer viewer, int offset) {
		while (current != null && !(current instanceof Element)) {
			current = current.getParentNode();
		}
		if (current == null) {
			return null;
		}
		if (current instanceof Element && "tile".equals(current.getNodeName())) { //$NON-NLS-1$
			final MavenProject prj = XmlUtils.extractMavenProject(viewer);
			final Element tile = (Element) current;
			IHyperlink tileHyperlink = new IHyperlink() {

				@Override
				public IRegion getHyperlinkRegion() {
					// the goal here is to have the groupid/artifactid/version combo underscored by the link.
					// that will prevent underscoring big portions (like plugin config) underscored and
					// will also handle cases like dependencies within plugins.
					int max = ((IndexedRegion) tile).getEndOffset();
					int min = ((IndexedRegion) tile).getStartOffset();
					return new Region(min, max - min);
				}

				@Override
				public String getHyperlinkText() {
					return MessageFormat.format(Messages.TileHyperlinkDetector_openTile, tile.getTextContent());
				}

				@Override
				public String getTypeLabel() {
					return "Maven Tile"; //$NON-NLS-1$
				}

				@Override
				public void open() {
					new Job(MessageFormat.format(Messages.TileHyperlinkDetector_openTile, tile.getTextContent())) {

						@Override
						protected IStatus run(IProgressMonitor monitor) {
							String[] gav = tokenizeWithProperties(tile.getTextContent());
							if (gav.length != 3) {
								return Status.CANCEL_STATUS;
							}
							String gridString = gav[0];
							String artidString = gav[1];
							String versionString = gav[2];
							if (prj != null
									&& gridString != null
									&& artidString != null
									&& (versionString == null || versionString.contains("${"))) { //$NON-NLS-1$
								try {
									versionString = extractVersion(prj, versionString, gridString, artidString);

								} catch (CoreException e) {
									versionString = null;
								}
							}
							if (versionString == null) {
								return Status.OK_STATUS;
							}
							openTileEditor(gridString, artidString, versionString, prj, monitor);
							return Status.OK_STATUS;
						}
					}.schedule();
				}

			};
			return tileHyperlink;
		}
		return null;
	}

	private IEditorPart openTileEditor(String groupId, String artifactId, String version, MavenProject project,
			IProgressMonitor monitor) {
		if (groupId.length() > 0 && artifactId.length() > 0) {
			final String name = groupId + ":" + artifactId + ":" + version + XML_EXTENSION; //$NON-NLS-1$ //$NON-NLS-2$
																							// //$NON-NLS-3$

			try {
				IMavenProjectRegistry projectManager = MavenPlugin.getMavenProjectRegistry();
				IMavenProjectFacade projectFacade = projectManager.getMavenProject(groupId, artifactId, version);
				if (projectFacade != null) {
					final IFile pomFile = projectFacade.getPom();
					return openTileEditor(new FileEditorInput((IFile) pomFile.getParent().findMember(TILE_XML)), name);
				}

				IMaven maven = MavenPlugin.getMaven();

				List<ArtifactRepository> artifactRepositories;
				if (project != null) {
					artifactRepositories = project.getRemoteArtifactRepositories();
				} else {
					artifactRepositories = maven.getArtifactRepositories();
				}

				Artifact artifact =
						maven.resolve(groupId, artifactId, version, "xml", "", artifactRepositories, monitor); //$NON-NLS-1$ //$NON-NLS-2$

				File file = artifact.getFile();
				if (file != null) {
					return openTileEditor(
							new MavenPathStorageEditorInput(name,
									name,
									file.getAbsolutePath(),
									readStream(new FileInputStream(file))),
							name);
				}

			} catch (final Exception ex) {
				String msg = NLS.bind(Messages.TileHyperlinkDetector_errorMessage, name, ex.toString());
				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

					@Override
					public void run() {
						MessageDialog.openInformation(Display.getDefault().getActiveShell(), //
								Messages.TileHyperlinkDetector_errorTitle,
								NLS.bind(Messages.TileHyperlinkDetector_errorMessage, name, ex.toString()));
					}
				});
			}
		}

		return null;
	}

	/**
	 * Open an XML Editor with the tile content.
	 */
	private IEditorPart openTileEditor(final IEditorInput editorInput, final String name) {
		final IEditorPart[] part = new IEditorPart[1];
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
				IContentType contentType = contentTypeManager.findContentTypeFor(name);
				IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
				IEditorDescriptor editor = editorRegistry.getDefaultEditor(name, contentType);
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window != null) {
					IWorkbenchPage page = window.getActivePage();
					if (page != null) {
						try {
							part[0] = page.openEditor(editorInput, editor.getId());
						} catch (PartInitException ex) {
							MessageDialog.openInformation(Display.getDefault().getActiveShell(), //
									Messages.TileHyperlinkDetector_errorTitle,
									NLS.bind(Messages.TileHyperlinkDetector_errorMessage, name, ex.toString()));
						}
					}
				}
			}
		});
		return part[0];
	}

	/**
	 * Tokenize a G:A:V and handle properties
	 */
	protected String[] tokenizeWithProperties(String value) {
		List<String> tokens = new ArrayList<>();
		int inProperty = 0;
		StringBuilder currentValue = new StringBuilder();
		next: for (int i = 0; i < value.length(); ++i) {
			char c = value.charAt(i);
			switch (c) {
			case '$':
				if ((i + 1) < value.length() && value.charAt(i + 1) == '{') {
					++inProperty;
					++i;
					currentValue.append("${"); //$NON-NLS-1$
					continue next;
				}
				break;

			case '}':
				if (inProperty > 0) {
					inProperty--;
				}
				break;

			case ':':
				if (inProperty <= 0) {
					tokens.add(currentValue.toString());
					currentValue = new StringBuilder();
					continue next;
				}
				break;

			default:
				break;
			}
			currentValue.append(c);
		}
		if (currentValue.length() > 0) {
			tokens.add(currentValue.toString());
		}
		return tokens.toArray(new String[tokens.size()]);
	}

	/**
	 * Read out a stream fully
	 */
	private static byte[] readStream(InputStream is) throws IOException {
		byte[] b = new byte[is.available()];
		int len = 0;
		while (true) {
			int n = is.read(b, len, b.length - len);
			if (n == -1) {
				if (len < b.length) {
					byte[] c = new byte[len];
					System.arraycopy(b, 0, c, 0, len);
					b = c;
				}
				return b;
			}
			len += n;
			if (len == b.length) {
				byte[] c = new byte[b.length + 1000];
				System.arraycopy(b, 0, c, 0, len);
				b = c;
			}
		}
	}

	/**
	 * Compute the effective version string
	 */
	private static String extractVersion(MavenProject mp, String version, String groupId, String artifactId)
			throws CoreException {

		assert mp != null;
		version = simpleInterpolate(mp, version);

		if (version == null) {
			Packaging pack = Packaging.ALL;
			version = searchDM(mp, groupId, artifactId);
		}
		return version;
	}

	/**
	 * Interpolate a version string
	 */
	private static String simpleInterpolate(MavenProject project, String text) {
		if (text != null && text.contains("${")) { //$NON-NLS-1$
			// when expression is in the version but no project instance around
			// just give up.
			if (project == null) {
				return null;
			}
			Properties props = project.getProperties();
			RegexBasedInterpolator inter = new RegexBasedInterpolator();
			if (props != null) {
				inter.addValueSource(new PropertiesBasedValueSource(props));
			}
			inter.addValueSource(new PrefixedObjectValueSource(Arrays.asList(new String[] {
					"pom.", "project." //$NON-NLS-1$ //$NON-NLS-2$
			}), project.getModel(), false));
			try {
				text = inter.interpolate(text);
			} catch (InterpolationException e) {
				text = null;
			}
		}
		return text;
	}

	/**
	 * Fill version from managed dependency
	 */
	static String searchDM(MavenProject project, String groupId, String artifactId) {
		if (project == null) {
			return null;
		}
		String version = null;
		// see if we can find the dependency is in dependency management of resolved project.
		String id = groupId + ":" + artifactId + ":"; //$NON-NLS-1$ //$NON-NLS-2$
		DependencyManagement dm = project.getDependencyManagement();
		if (dm != null) {
			for (Dependency dep : dm.getDependencies()) {
				if (dep.getManagementKey().startsWith(id)) {
					version = dep.getVersion();
					break;
				}
			}
		}
		return version;
	}

}
